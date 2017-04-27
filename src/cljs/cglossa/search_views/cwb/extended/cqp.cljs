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
  (when (seq values)
    ;; If the values start with exclamation marks, remove them and change the
    ;; operator from = to !=, yielding expressions of the type attr!=val1|val2
    (let [op      (if (str/starts-with? (first values) "!") "!=" "=")
          values* (map #(str/replace % #"^!" "") values)]
      (str attr-name op "\"" (str/join "|" values*) "\""))))


(defn- process-pos-map [[pos attrs]]
  (let [op           (if (str/starts-with? pos "!") "!=" "=")
        pos*         (str/replace pos #"^!" "")
        attr-strings (map process-attr-map attrs)
        attr-str     (when (seq attr-strings)
                       (str " & " (str/join " & " attr-strings)))]
    (str "(pos" op "\"" pos* "\"" attr-str ")")))


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


;; Unescapes any escaped chars, since we don't want the backslashes to show in the text input
(defn- unescape-form [form]
  (-> form
      (str/replace #"\\(.)" "$1")
      (str/replace #"^(?:\.\*)?(.+?)" "$1")
      (str/replace #"(.+?)(?:\.\*)?$" "$1")))


(defn- process-first-form [term name op val]
  (cond-> (assoc term :form (unescape-form val))
          (= name "lemma") (assoc :lemma? true)
          (= name "phon") (assoc :phonetic? true)
          (= name "orig") (assoc :original? true)
          (re-find #".+\.\*$" val) (assoc :start? true)
          (re-find #"^\.\*.+" val) (assoc :end? true)))


(defn- process-other-forms [term name op val]
  (update-in term [:extra-forms name] #(if % (conj % val) (set [val]))))


(defn- handle-attribute-value [terms part interval corpus-specific-attrs-regex]
  (let [process-forms (fn [t p]
                        (let [forms (for [[_ name op val] (re-seq #"(word|lemma|phon|orig)\s*(!?=)\s*\"(.+?)\"" p)
                                          :let [val* (if (= op "!=") (str "!" val) val)]]
                                      [name op val*])]
                          (reduce (fn [acc [name op val]]
                                    ;; If the attribute value starts with &&, it should be a special
                                    ;; code (e.g. for marking errors in text, inserted as "tokens"
                                    ;; to ensure alignment between original and corrected text) and
                                    ;; should not be shown in the text input box
                                    (if (and name (not (str/starts-with? val "&&")))
                                      ;; Only the first non-negative word/lemma/phon/orig form
                                      ;; goes into the form attribute; the rest are :extra-forms
                                      (if (and (str/blank? (:form acc)) (= op "="))
                                        (process-first-form acc name op val)
                                        (process-other-forms acc name op val))
                                      acc))
                                  t
                                  forms)))
        term          (as-> {:interval @interval} $
                            (process-forms $ (last part))
                            #_(let [[_ name op val] (re-find #"(word|lemma|phon|orig)\s*(!?=)\s*\"(.+?)\""
                                                             (last part))
                                    val* (if (= op "!=") (str "!" val) val)]
                                ;; If the attribute value starts with &&, it should be a special code
                                ;; (e.g. for marking errors in text, inserted as "tokens" to ensure alignment
                                ;; between original and corrected text) and should not be shown in the text
                                ;; input box
                                (if (and name (not (str/starts-with? val* "&&")))
                                  (process-first-form $ name op val*)
                                  $))
                            (if-let [pos-exprs (re-seq #"\(pos\s*(!?=)\s*\"(.+?)\"(.*?)\)" (last part))]
                              (reduce (fn [t [_ pos-op pos rest]]
                                        ;; Allow attribute values to contain Norwegian chars,
                                        ;; -, <, >, :, ., and /
                                        ;; in addition to alphanumeric characters
                                        (let [others (re-seq #"(\w+)\s*(!?=)\s*\"([\w\|\-\<\>:\./æøå]+)\""
                                                             rest)]
                                          (assoc-in t [:features (if (= pos-op "!=") (str "!" pos) pos)]
                                                    (into {} (map (fn [[_ name val-op vals]]
                                                                    [name
                                                                     (as-> vals $
                                                                           (str/split $ #"\|")
                                                                           (map (fn [val]
                                                                                  (if (= val-op "!=")
                                                                                    (str "!" val)
                                                                                    val))
                                                                                $)
                                                                           (set $))])
                                                                  others)))))
                                      $
                                      pos-exprs)
                              $)
                            (if-let [exprs (when corpus-specific-attrs-regex
                                             (re-seq corpus-specific-attrs-regex (last part)))]
                              (reduce (fn [t [_ attr vals]]
                                        ;; If the attribute is "word" or "orig", we only allow values
                                        ;; starting with && to be treated as corpus-specific values
                                        ;; (other values are just ordinary word forms to search for)
                                        (if (or (nil? (#{"word" "orig"} attr))
                                                (str/starts-with? vals "&&"))
                                          (assoc-in t [:corpus-specific-attrs attr]
                                                    (set (str/split vals #"\|")))
                                          t))
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


(defn- process-extra-forms [name vals]
  (let [pos-and-neg     (group-by #(str/starts-with? % "!") vals)
        positives       (get pos-and-neg false)
        negatives       (get pos-and-neg true)
        has-positives?  (seq positives)
        considered-vals (if has-positives?
                          ;; If there are any positives (e.g. word="hello"), we can ignore any
                          ;; negatives, since they are redundant
                          positives
                          ;; Else use the negatives (with the initial exclamation points removed)
                          (map #(subs % 1) negatives))
        operation       (if has-positives? "=" "!=")
        separator       (if has-positives? " | " " & ")]
    (str "("
         (str/join separator
                   (for [val considered-vals]
                     (str name operation "\"" val "\"")))
         ")")))


(defn terms->query [wrapped-query terms query-term-ids lang-config corpus]
  (let [;; Remove ids whose corresponding terms have been set to nil
        _       (swap! query-term-ids #(vec (keep-indexed (fn [index id]
                                                            (when (nth terms index) id)) %)))
        s-tag   (if (= (:search-engine corpus) "cwb_speech") "sync" "s")
        terms*  (filter identity terms) ; nil means term should be removed
        parts   (for [{:keys [interval form lemma? phonetic? original?
                              start? end? features extra-forms corpus-specific-attrs]} terms*]
                  (let [attr         (cond
                                       lemma? "lemma"
                                       phonetic? "phon"
                                       original? "orig"
                                       :else "word")
                        form*        (if (empty? form)
                                       (when (and (empty? features)
                                                  (empty? extra-forms)
                                                  (empty? corpus-specific-attrs))
                                         ".*")
                                       (cond-> form
                                               ;; Escape special characters using a regex from
                                               ;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/
                                               ;;   Guide/Regular_Expressions
                                               true (str/replace #"[\.\*\+\?\^\$\{\}\(\)\|\[\]\\]"
                                                                 "\\$&")
                                               start? (str ".*")
                                               end? (#(str ".*" %))))
                        main         (when form*
                                       (str attr "=\"" form* "\"" (when-not phonetic? " %c")))
                        feats        (when (seq features)
                                       ;; If we want to exclude parts of speech, join them with &
                                       ;; (e.g. [pos != "noun" & pos != "verb"]), otherwise with |
                                       ;; (e.g. [pos = "noun" | pos = "verb"])
                                       (let [op (if (str/starts-with? (ffirst features) "!") " & " " | ")]
                                         (str "(" (str/join op (map process-pos-map features)) ")")))
                        extra-forms* (when (seq extra-forms)
                                       (str/join " & "
                                                 (for [[name vals] extra-forms]
                                                   (process-extra-forms name vals))))
                        extra        (when (seq corpus-specific-attrs)
                                       (str/join " & " (filter identity
                                                               (map process-attr-map
                                                                    corpus-specific-attrs))))
                        [min max] interval
                        interv       (if (or min max)
                                       (str "[]{" (or min 0) "," (or max "") "} ")
                                       "")]
                    (str interv "[" (str/join " & " (filter identity [main feats extra-forms* extra])) "]")))
        query*  (str/join \space parts)
        query** (if-let [pos-attr (:pos-attr lang-config)]
                  (str/replace query* #"\bpos(?=\s*!?=)" pos-attr)
                  query*)
        query   (cond->> query**
                         (:segment-initial? @wrapped-query) (str "<" s-tag ">")
                         (:segment-final? @wrapped-query) (#(str % "</" s-tag ">")))]
    query))


(defn set-pos-attr
  "If the current language names the part-of-speech attribute something other
   than 'pos', replace it with 'pos' in order to simplify other extended-view
   code."
  [wrapped-query lang-config]
  (let [query (:query @wrapped-query)]
    (if-let [pos-attr (:pos-attr lang-config)]
      (str/replace query
                   (re-pattern (str "\\b" pos-attr "(?=\\s*!?=)"))
                   "pos")
      query)))
