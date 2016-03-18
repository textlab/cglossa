(ns cglossa.search-views.cwb.extended.cqp
  (:require [clojure.string :as str]))

(defn- combine-regexes [regexes]
  "Since there is no way to concatenate regexes directly, we convert
  them to strings, remove the initial and final slash from each one,
  concatenate the resulting strings with a pipe symbol, and finally
  convert the concatenated string back to a single regex."
  (->> regexes
       (map str)
       (map (partial re-matches #"/(.+)/"))
       (map last)
       (str/join \|)
       re-pattern))

;; An interval, e.g. []{1,2}
(def interval-rx #"\[\]\{(.+?)\}")

;; An attribute/value expression such as [lemma="car" %c] or [(lemma="car" & pos="n")].
;; Treat quoted strings separately; they may contain right brackets
(def attribute-value-rx #"\[\(?([^\"]+?(?:\"[^\"]*\"[^\]\"]*?)*?)(?:\s+%c)?\)?\]")

;; A quoted string or a single unspecified token
(def quoted-or-empty-term-rx #"\".*?\"|\[\]")

(def terms-rx (combine-regexes [interval-rx quoted-or-empty-term-rx attribute-value-rx]))


(defn- split-query [query]
  (let [terms (if (str/blank? query)
                query
                (re-seq terms-rx query))]
    (if (str/blank? terms)
      [["[]"]]
      terms)))


(defn- process-attr-map [[attr-name values]]
  (str attr-name "=\"" (str/join "|" values) "\""))


(defn- process-pos-map [[pos attrs]]
  (let [attr-strings (map process-attr-map attrs)
        attr-str     (when (seq attr-strings)
                       (str " & " (str/join " & " attr-strings)))]
    (str "(pos=\"" pos "\"" attr-str ")")))


(defn- handle-interval [terms part interval]
  (let [values (second part)
        min    (some->> values
                        (re-find #"(\d+),")
                        last)
        max    (some->> values
                        (re-find #",(\d+)")
                        last)]
    (reset! interval [min max])
    terms))


(defn- process-form [term name val]
  (cond-> (assoc term :form (-> val
                                (str/replace #"^(?:\.\*)?(.+?)" "$1")
                                (str/replace #"(.+?)(?:\.\*)?$" "$1")))
          (= name "lemma") (assoc :lemma? true)
          (= name "phon") (assoc :phonetic? true)
          (re-find #".+\.\*$" val) (assoc :start? true)
          (re-find #"^\.\*.+" val) (assoc :end? true)))


(defn- handle-attribute-value [terms part interval corpus-specific-attrs-regex]
  (let [term (as-> {:interval @interval} $
                   (if-let [[_ name val] (re-find #"(word|lemma|phon)\s*=\s*\"(.+?)\""
                                                  (last part))]
                     (process-form $ name val)
                     $)
                   (if-let [pos-exprs (re-seq #"\(pos=\"(.+?)\"(.*?)\)" (last part))]
                     (reduce (fn [t [_ pos rest]]
                               ;; Allow attribute values to contain -, <, > and / in addition
                               ;; to alphanumeric characters
                               (let [others (re-seq #"(\w+)=\"([\w\|\-\<\>/]+)\"" rest)]
                                 (assoc-in t [:features pos]
                                           (into {} (map (fn [[_ name vals]]
                                                           [name
                                                            (set (str/split vals
                                                                            #"\|"))])
                                                         others)))))
                             $
                             pos-exprs)
                     $)
                   (if-let [exprs (when corpus-specific-attrs-regex
                                    (re-seq corpus-specific-attrs-regex (last part)))]
                     (reduce (fn [t [_ attr vals]]
                               (assoc-in t [:corpus-specific-attrs attr]
                                         (set (str/split vals #"\|"))))
                             $
                             exprs)
                     $))]
    (reset! interval [nil nil])
    (conj terms term)))


(defn- handle-quoted-or-empty-term [terms part interval]
  (let [p    (first part)
        len  (count p)
        form (if (> len 2)
               (subs p 1 len)
               "")
        term (cond-> {:form     form
                      :interval @interval}
                     (re-find #".+\.\*$" form)
                     (assoc :start? true)

                     (re-find #"^\.\*.+" form)
                     (assoc :end? true))]
    (reset! interval [nil nil])
    (conj terms term)))


(defn query->terms [query corpus-specific-attrs]
  ;; Use an atom to keep track of interval specifications so that we can set
  ;; them as the value of the :interval key in the map representing the following
  ;; query term.
  (let [interval                    (atom [nil nil])
        corpus-specific-attr-names  (when (seq corpus-specific-attrs)
                                      (str "(" (str/join "|" corpus-specific-attrs) ")"))
        corpus-specific-attrs-regex (when corpus-specific-attr-names
                                      (re-pattern (str corpus-specific-attr-names "=\"(.+?)\"")))]
    (reduce (fn [terms part]
              (condp re-matches (first part)
                interval-rx
                (handle-interval terms part interval)

                attribute-value-rx
                (handle-attribute-value terms part interval corpus-specific-attrs-regex)

                quoted-or-empty-term-rx
                (handle-quoted-or-empty-term terms part interval)))
            []
            (split-query query))))


(defn terms->query [terms query-term-ids lang-config]
  (let [;; Remove ids whose corresponding terms have been set to nil
        _      (swap! query-term-ids #(vec (keep-indexed (fn [index id]
                                                           (when (nth terms index) id)) %)))
        terms* (filter identity terms)                      ; nil means term should be removed
        parts  (for [{:keys [interval form lemma? phonetic?
                             start? end? features corpus-specific-attrs]} terms*]
                 (let [attr   (cond
                                lemma? "lemma"
                                phonetic? "phon"
                                :else "word")
                       form*  (if (empty? form)
                                (when (empty? features) ".*")
                                (cond-> form
                                        ;; Escape special characters using a regex from
                                        ;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/
                                        ;;   Guide/Regular_Expressions
                                        ;; with the addition of quotes (since quotes enclose
                                        ;; regexes in CQP)
                                        true (str/replace #"[\"\.\*\+\?\^\$\{\}\(\)\|\[\]\\]"
                                                          "\\$&")
                                        start? (str ".*")
                                        end? (#(str ".*" %))))
                       main   (when form*
                                (str attr "=\"" form* "\" %c"))
                       feats  (when (seq features)
                                (str "(" (str/join " | " (map process-pos-map features)) ")"))
                       extra  (when (seq corpus-specific-attrs)
                                (str/join " & " (map process-attr-map corpus-specific-attrs)))
                       [min max] interval
                       interv (if (or min max)
                                (str "[]{" (or min 0) "," (or max "") "} ")
                                "")]
                   (str interv "[" (str/join " & " (filter identity [main feats extra])) "]")))
        query* (str/join \space parts)
        query  (if-let [pos-attr (:pos-attr lang-config)]
                 (str/replace query* #"\bpos(?=\s*=)" pos-attr)
                 query*)]
    query))


(defn set-pos-attr
  "If the current language names the part-of-speech attribute something other
   than 'pos', replace it with 'pos' in order to simplify other extended-view
   code."
  [wrapped-query lang-config]
  (let [query (:query @wrapped-query)]
    (if-let [pos-attr (:pos-attr lang-config)]
      (str/replace query
                   (re-pattern (str "\\b" pos-attr "(?=\\s*=)"))
                   "pos")
      query)))
