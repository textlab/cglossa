(ns cglossa.search-views.cwb.extended
  "Implementation of search view component with text inputs, checkboxes
  and menus for easily building complex and grammatically specified queries."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [cglossa.shared :refer [on-key-down remove-row-btn headword-search-checkbox]]
            [cglossa.react-adapters.bootstrap :as b]))

(enable-console-print!)

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

(defn split-query [query]
  (let [terms (if (str/blank? query)
                query
                (re-seq terms-rx query))]
    (if (str/blank? terms)
      [["[]"]]
      terms)))

(defn- process-form [term name val]
  (cond-> (assoc term :form (-> val
                                (str/replace #"^(?:\.\+)?(.+?)" "$1")
                                (str/replace #"(.+?)(?:\.\+)?$" "$1")))
          (= name "lemma") (assoc :lemma? true)
          (= name "phon") (assoc :phonetic? true)
          (re-find #"\.\+$" val) (assoc :start? true)
          (re-find #"^\.\+" val) (assoc :end? true)))


(defn construct-query-terms [parts]
  ;; Use an atom to keep track of interval specifications so that we can set
  ;; them as the value of the :interval key in the map representing the following
  ;; query term.
  (let [interval (atom [nil nil])]
    (reduce (fn [terms part]
              (condp re-matches (first part)
                interval-rx
                (let [values (second part)
                      min    (some->> values
                                      (re-find #"(\d+),")
                                      last)
                      max    (some->> values
                                      (re-find #",(\d+)")
                                      last)]
                  (reset! interval [min max])
                  terms)

                attribute-value-rx
                (let [term (as-> {:interval @interval} $
                                 (if-let [[_ name val] (re-find #"(word|lemma|phon)\s*=\s*(\S+)"
                                                                (last part))]
                                   (process-form $ name val)
                                   $)
                                 (if-let [pos-exprs (re-seq #"\(pos=\"(.+?)\"(.*?)\)" (last part))]
                                   (reduce (fn [t [_ pos rest]]
                                             (let [others (re-seq #"(\w+)=\"([\w\|/]+)\"" rest)]
                                               (assoc-in t [:features pos]
                                                         (into {} (map (fn [[_ name vals]]
                                                                         [name
                                                                          (set (str/split vals #"\|"))])
                                                                       others)))))
                                           $
                                           pos-exprs)
                                   $))]
                  (reset! interval [nil nil])
                  (conj terms term))

                quoted-or-empty-term-rx
                (let [p    (first part)
                      len  (count p)
                      form (if (> len 2)
                             (subs p 1 len)
                             "")
                      term (cond-> {:form     form
                                    :interval @interval}
                                   (re-find #"\.\+$" form)
                                   (assoc :start? true)

                                   (re-find #"^\.\+" form)
                                   (assoc :end? true))]
                  (reset! interval [nil nil])
                  (conj terms term))))
            []
            parts)))

(defn- process-attr-map [[attr-name values]]
  (str attr-name "=\"" (str/join "|" values) "\""))

(defn- process-pos-map [[pos attrs]]
  (let [attr-strings (map process-attr-map attrs)
        attr-str     (when (seq attr-strings)
                       (str " & " (str/join " & " attr-strings)))]
    (str "(pos=\"" pos "\"" attr-str ")")))

(defn- construct-cqp-query [terms query-term-ids]
  (let [;; Remove ids whose corresponding terms have been set to nil
        _      (swap! query-term-ids #(keep-indexed (fn [index id] (when (nth terms index) id)) %))
        terms* (filter identity terms)                      ; nil means term should be removed
        parts  (for [{:keys [interval form lemma? phonetic? start? end? features]} terms*]
                 (let [attr   (cond
                                lemma? "lemma"
                                phonetic? "phon"
                                :else "word")
                       form*  (if (empty? form)
                                (when (empty? features) ".*")
                                (cond-> form
                                        start? (str ".+")
                                        end? (#(str ".+" %))))
                       main   (when form*
                                (str attr "=" form* " %c"))
                       feats  (when (seq features)
                                (str "(" (str/join " | " (map process-pos-map features)) ")"))
                       [min max] interval
                       interv (if (or min max)
                                (str "[]{" (or min 0) "," (or max "") "} ")
                                "")]
                   (str interv "[" (str/join " & " (filter identity [main feats])) "]")))]
    (str/join \space parts)))

(defn wrapped-term-changed [wrapped-query terms index query-term-ids term]
  (swap! wrapped-query assoc :query (construct-cqp-query (assoc terms index term) query-term-ids)))

;;;;;;;;;;;;;;;;
;;;; Components
;;;;;;;;;;;;;;;;

(defn- menu-button [{:keys [menu-data]} wrapped-term]
  ;(.log js/console @menu-data)
  ;(println "wrapped-term:" @wrapped-term)
  (list
    ^{:key "btn"}
    [b/button "Hei"]
    ^{:key "modal"}
    [b/modal {:bs-size "large"
              :show    true
              :on-hide #()}
     [b/modalbody
      [b/panel {:header "Parts-of-speech"}
       (doall (for [[pos title morphsyn] @menu-data
                    :let [selected? (contains? (:features @wrapped-term) pos)]]
                ^{:key pos}
                [b/button
                 {:style    {:margin-left 3}
                  :bs-size  "small"
                  :bs-style (if selected? "info" "default")
                  :on-click (fn [_] (swap! wrapped-term update :features
                                           #(if selected? (dissoc % pos) (assoc % pos {})))
                              ;(println "w:" (get-in @wrapped-term [:features]))
                              )}
                 title]))]
      (for [[pos title morphsyn] @menu-data
            :when (and (contains? (:features @wrapped-term) pos)
                       (seq morphsyn))]
        ^{:key pos}
        [b/panel {:header (str "Morphosyntactic features for " title)}
         [:div.table-display
          (for [[header attrs] (partition 2 morphsyn)]
            ^{:key header}
            [:div.table-row
             [:div.table-cell header ": "]
             [:div.table-cell {:style {:padding-bottom 5}}
              (doall (for [[attr value title] attrs
                           :let [selected? (contains? (get-in @wrapped-term [:features pos (name attr)])
                                                      value)]]
                       ^{:key value}
                       [b/button {:style    {:margin-left 3}
                                  :bs-size  "small"
                                  :bs-style (if selected? "info" "default")
                                  :on-click (fn [_]
                                              ;(.log js/console (get-in @wrapped-term [:features]))
                                              (swap! wrapped-term update-in [:features pos (name attr)]
                                                     (fn [a] (if selected?
                                                               (disj a value)
                                                               (set (conj a value))))))}
                        title]))]])]])]
     [b/modalfooter
      [b/button {:on-click #()} "Close"]]]))

(defn- text-input [a m wrapped-term show-remove-term-btn?]
  [:div.table-cell
   [b/input {:type          "text"
             :bs-size       "small"
             :style         {:font-size 14
                             :width     (if show-remove-term-btn? 108 140)}
             :button-before (r/as-element (menu-button m wrapped-term))
             :button-after  (when show-remove-term-btn?
                              (r/as-element [b/button {:on-click #(reset! wrapped-term nil)}
                                             [b/glyphicon {:glyph "minus"}]]))
             :default-value (str/replace (:form @wrapped-term) #"^\.\*$" "")
             :on-change     #(swap! wrapped-term assoc :form (.-target.value %))
             :on-key-down   #(on-key-down % a m)}]])

(defn- add-term-btn [wrapped-query query-term-ids]
  [:div.table-cell {:style {:vertical-align "bottom" :padding-left 14 :padding-bottom 5}}
   [b/button {:bs-style "info"
              :bs-size  "xsmall"
              :title    "Add search word"
              :on-click (fn []
                          ; Append greatest-current-id-plus-one to the
                          ; query-term-ids vector
                          (swap! query-term-ids
                                 #(conj % (inc (max %))))
                          ; Append [] to the CQP query expression
                          (swap! wrapped-query
                                 update :query str " []"))}
    [b/glyphicon {:glyph "plus"}]]])

(defn- interval-input [a m wrapped-term index]
  [b/input {:type        "text"
            :bs-size     "small"
            :class-name  "interval"
            :value       (get-in @wrapped-term [:interval index])
            :on-change   #(swap! wrapped-term
                                 assoc-in [:interval index] (.-target.value %))
            :on-key-down #(on-key-down % a m)}])

(defn interval [a m wrapped-term]
  [:div.interval.table-cell
   [interval-input a m wrapped-term 0] "min"
   [:br]
   [interval-input a m wrapped-term 1] "max"])

(defn- checkboxes [wrapped-term has-phonetic?]
  (let [term-val @wrapped-term]
    [:div.table-cell {:style {:min-width 182}}
     [:div.word-checkboxes
      [:label.checkbox-inline {:style {:padding-left 15}}
       [:input {:type      "checkbox"
                :style     {:margin-left -15}
                :checked   (:lemma? term-val)
                :on-change #(swap! wrapped-term assoc :lemma? (.-target.checked %))
                }] "Lemma"]
      [:label.checkbox-inline {:style {:padding-left 15}}
       [:input {:type      "checkbox"
                :style     {:margin-left -15}
                :title     "Start of word"
                :checked   (:start? term-val)
                :on-change #(swap! wrapped-term assoc :start? (.-target.checked %))
                }] "Start"]
      [:label.checkbox-inline {:style {:padding-left 15}}
       [:input {:type      "checkbox"
                :style     {:margin-left -15}
                :title     "End of word"
                :checked   (:end? term-val)
                :on-change #(swap! wrapped-term assoc :end? (.-target.checked %))
                }] "End"]]
     (when has-phonetic?
       [:div>label.checkbox-inline {:style {:padding-left 15}}
        [:input {:type      "checkbox"
                 :style     {:margin-left -15}
                 :checked   (:phonetic? term-val)
                 :on-change #(swap! wrapped-term assoc :phonetic? (.-target.checked %))
                 }] "Phonetic form"])]))

(defn- taglist []
  [:div.tag-list.table-cell {:ref "taglist"}
   [:div.tags]])

(defn multiword-term [a m wrapped-query wrapped-term query-term-ids
                      first? last? has-phonetic? show-remove-row-btn?
                      show-remove-term-btn?]
  [:div.table-cell>div.multiword-term>div.control-group
   [:div.table-row
    (when first?
      [remove-row-btn show-remove-row-btn? wrapped-query])
    [text-input a m wrapped-term show-remove-term-btn?]
    (when last?
      [add-term-btn wrapped-query query-term-ids])]

   [:div.table-row
    (when first?
      [:div.table-cell])
    [checkboxes wrapped-term has-phonetic?]
    (when last?
      [:div.table-cell])]

   [:div.table-row
    (when first?
      [:div.table-cell])
    [taglist]
    (when last?
      [:div.table-cell])]])


(defn extended
  "Search view component with text inputs, checkboxes and menus
  for easily building complex and grammatically specified queries."
  [_ _ _ _]
  (let [;; This will hold a unique ID for each query term component. React wants a
        ;; unique key for each component in a sequence, such as the set of search inputs
        ;; in the multiword interface, and it will mess up the text in the search boxes
        ;; when we remove a term from the query if we don't provide this. Using the index
        ;; of the term is meaningless, since it does not provide any more information
        ;; than the order of the term itself. What we need is a way to uniquely identify
        ;; each term irrespective of ordering.
        ;;
        ;; Normally, the items in a list have some kind of database ID that we can use,
        ;; but query terms don't. Also, we cannot just use a hash code created from the
        ;; term object, since we may have several identical terms in a query. Hence, we
        ;; need to provide this list of query term IDs containing a unique ID number for
        ;; each term in the initial query (i.e., the one provided in the props when this
        ;; component is mounted), and then we add a newly created ID when adding a query
        ;; term in the multiword interface and remove the ID from the list when the term is
        ;; removed. This is the kind of ugly state manipulation that React normally saves
        ;; us from, but in cases like this it seems unavoidable...
        query-term-ids (atom nil)]
    (fn [a {:keys [corpus] :as m} wrapped-query show-remove-row-btn?]
      (let [parts           (split-query (:query @wrapped-query))
            terms           (construct-query-terms parts)
            last-term-index (dec (count terms))]
        (when (nil? @query-term-ids)
          (reset! query-term-ids (range (count terms))))
        [:div.multiword-container
         [:form.form-inline.multiword-search-form {:style {:margin-left -35}}
          [:div.table-display
           [:div.table-row
            (doall
              (map-indexed (fn [index term]
                             (let [wrapped-term          (r/wrap term
                                                                 wrapped-term-changed
                                                                 wrapped-query terms index
                                                                 query-term-ids)
                                   term-id               (nth @query-term-ids index)
                                   first?                (zero? index)
                                   last?                 (= index last-term-index)
                                   ;; Show buttons to remove terms if there is more than one term
                                   show-remove-term-btn? (pos? last-term-index)
                                   has-phonetic?         (:has-phonetic @corpus)]
                               (list (when-not first?
                                       ^{:key (str "interval" term-id)}
                                       [interval a m wrapped-term corpus])
                                     ^{:key (str "term" term-id)}
                                     [multiword-term a m wrapped-query wrapped-term query-term-ids
                                      first? last? has-phonetic? show-remove-row-btn?
                                      show-remove-term-btn?])))
                           terms))]
           (when (:has-headword-search @corpus)
             [:div.table-row
              [:div.table-cell {:style {:padding-left 40 :padding-top 10}}
               [headword-search-checkbox wrapped-query]]])]]]))))
