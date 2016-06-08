(ns cglossa.search-views.cwb.core
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.dom :as dom]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.shared :refer [search! on-key-down remove-row-btn segment-initial-checkbox
                                    segment-final-checkbox headword-search-checkbox]]
            [cglossa.search-views.shared :refer [search-inputs has-phonetic?]]
            [cglossa.search-views.cwb.extended.core :refer [extended]]))

(def ^:private headword-query-prefix "<headword>")
(def ^:private headword-query-suffix-more-words "[]{0,}")
(def ^:private headword-query-suffix-tag "</headword>")

(defn- ->headword-query [query]
  (str headword-query-prefix
       query
       headword-query-suffix-more-words
       headword-query-suffix-tag))

(defn- without-prefix [s prefix]
  (let [prefix-len (count prefix)]
    (if (= (subs s 0 prefix-len) prefix)
      (subs s prefix-len)
      s)))

(defn- without-suffix [s suffix]
  (let [suffix-start (- (count s) (count suffix))]
    (if (= (subs s suffix-start) suffix)
      (subs s 0 suffix-start)
      s)))

(defn- ->non-headword-query [query]
  (-> query
      (without-suffix headword-query-suffix-tag)
      (without-suffix headword-query-suffix-more-words)
      (without-prefix headword-query-prefix)))

(defn- phrase->cqp [phrase phonetic?]
  (let [attr       (if phonetic? "phon" "word")
        chinese-ch "[\u4E00-\u9FFF\u3400-\u4DFF\uF900-\uFAFF]"
        ; Surround every Chinese character by space when constructing a cqp query,
        ; to treat it as if it was an individual word:
        p1         (str/replace phrase
                                (re-pattern (str "(" chinese-ch ")"))
                                " $1 ")
        p2         (as-> (str/split p1 #"\s") $
                         ;; Replace literal quotes with __QUOTE__ to prevent them from confusing
                         ;; our regexes later on
                         (map #(str/replace % "\"" "__QUOTE__") $)
                         ;; Escape other special characters using a regex from
                         ;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/
                         ;;   Guide/Regular_Expressions
                         (map #(str/replace % #"[\.\*\+\?\^\$\{\}\(\)\|\[\]\\]" "\\$&") $)
                         (map #(if (= % "")
                                ""
                                (str "[" attr "=\"" % "\" %c]"))
                              $)
                         (str/join " " $)
                         (str/replace $
                                      (re-pattern (str "\\s(\\[\\w+=\""
                                                       chinese-ch
                                                       "\"(?:\\s+%c)?\\])\\s"))
                                      "$1")
                         ;; NOTE: In JavaScript, "han ".split(/\s/) yields the array
                         ;; ["han", " "], but in ClojureScript (str/split "han " #"\s")
                         ;; only yields ["han"]. Hence, in the CLJS version we need to
                         ;; add the extra element if the query ends in a space.
                         (if (= \space (last (seq p1))) (str $ " ") $))]
    (if (str/blank? p2)
      (str "[" attr "=\".*\" %c]")
      p2)))

(defn- focus-text-input [c]
  ;; Use (aget % "type") instead of (.-type %) simply because the latter makes the syntax
  ;; checker in Cursive Clojure go bananas for some reason...
  (.focus (dom/findNode (rdom/dom-node c) #(#{"text" "textarea"} (aget % "type")))))

(defn- wrapped-query-changed [queries index query-ids query]
  "Takes a changed query, performs some cleanup on it, and swaps it into
  the appropriate position in the vector of queries that constitutes the
  current search. If the query is nil, removes it from the vector instead."
  (if (nil? query)
    (do
      (swap! query-ids #(into (subvec % 0 index)
                              (subvec % (inc index))))
      (swap! queries #(into (subvec % 0 index)
                            (subvec % (inc index)))))
    (let [query-expr (as-> (:query query) $
                           (if (:headword-search query)
                             (->headword-query $)
                             (->non-headword-query $))
                           ;; Simplify the query (".*" is used in the simple search instead of [])
                           (str/replace $ #"\[\(?word=\"\.\*\"(?:\s+%c)?\)?\]" "[]")
                           (str/replace $ #"^\s*\[\]\s*$" ""))
          query*     (assoc query :query query-expr)]
      (swap! queries assoc index query*))))

(defn- add-row [queries query-ids query]
  (swap! queries conj query)
  ; Append greatest-current-id-plus-one to the query-ids vector
  (swap! query-ids #(conj % (inc (apply max %)))))

;;;;;;;;;;;;;;;;;
; Event handlers
;;;;;;;;;;;;;;;;;

(defn- on-phonetic-changed [event wrapped-query]
  (let [q        (:query @wrapped-query)
        checked? (.-target.checked event)
        query    (if checked?
                   (if (str/blank? q)
                     "[phon=\".*\" %c]"
                     (str/replace q "word=" "phon="))
                   (str/replace q "phon=" "word="))]
    (swap! wrapped-query assoc :query query)))

;;;;;;;;;;;;;
; Components
;;;;;;;;;;;;;

(defn- search-button [a m margin-left]
  [b/button {:bs-style "success"
             :style    {:margin-left margin-left}
             :on-click #(search! a m)} "Search"])

(defn- add-row-button [queries view text on-click]
  [b/button {:bs-size  "small"
             :style    {:margin-right 10
                        :margin-top   (if (= view extended) -15 0)}
             :disabled (-> @queries first :query str/blank?)
             :on-click on-click}
   text])

(defn- add-language-button [{{:keys [queries query-ids]} :search-view} {:keys [corpus]} view]
  [add-row-button queries view "Add language"
   (fn [_]
     (let [all-langs  (->> @corpus :languages (map :code) set)
           used-langs (->> @queries (map :lang) set)
           ;; The default language for the new row will be the first available
           ;; language that has not been used so far
           lang       (first (set/difference all-langs used-langs))]
       (add-row queries query-ids {:query "" :lang lang})))])

(defn- add-phrase-button [{{:keys [queries query-ids]} :search-view} {:keys [corpus]} view]
  [add-row-button queries view "Or..."
   (fn [_]
     (let [lang (-> @corpus :languages first :code)]
       (add-row queries query-ids {:query "" :lang lang})))])

(defn- show-texts-button [{:keys [show-texts?]} view]
  [b/button {:bs-size  "small"
             :style    {:margin-top (if (= view extended) -15 0)}
             :on-click (fn [e]
                         (reset! show-texts? true)
                         (.preventDefault e))}
   "Show texts"])

(defn- language-select [wrapped-query languages queries]
  (let [selected-language     (or (:lang @wrapped-query) (-> languages first :code))
        previously-used-langs (disj (->> @queries (map :lang) set) selected-language)]
    [b/input {:type          "select"
              :bs-size       "small"
              :style         {:width 166}
              :default-value selected-language
              :on-change     #(reset! wrapped-query {:query "" :lang (keyword (.-target.value %))})}
     (for [{:keys [code name]} languages
           :when (not (get previously-used-langs code))]
       [:option {:key code :value code} name])]))

(defn- single-input-view
  "HTML that is shared by the search views that only show a single text input,
  i.e., the simple and CQP views."
  [a {:keys [corpus] :as m} input-type wrapped-query displayed-query
   show-remove-row-btn? show-checkboxes? on-text-changed]
  (let [query     (:query @wrapped-query)
        phonetic? (not= -1 (.indexOf query "phon="))]
    [:form.table-display {:style {:margin "10px 0px 15px -35px"}}
     [:div.table-row {:style {:margin-bottom 10}}
      [remove-row-btn show-remove-row-btn? wrapped-query]
      [b/input {:style            {:width 500}
                :class-name       "col-sm-12"
                :group-class-name "table-cell"
                :type             input-type
                :default-value    displayed-query
                :on-change        #(on-text-changed % wrapped-query phonetic?)
                :on-key-down      #(on-key-down % a m)}]]
     (when show-checkboxes?
       (list ^{:key 1}
             [:div.table-row
              [:div.table-cell]
              ;; ReactBootstrap doesn't seem to allow several checkboxes within the same
              ;; div.checkbox, since each [input {:type "checkbox"}] generates its own div.checkbox
              ;; wrapper (or is it possible somehow?), so we create the markup manually instead.
              [:div.checkbox {:style {:display "table-cell" :padding-top 7}}
               (when (has-phonetic? @corpus)
                 (list
                   ^{:key "phon"}
                   [:label.checkbox-inline {:style {:padding-left 18}}
                    [:input {:name      "phonetic"
                             :type      "checkbox"
                             :style     {:margin-left -18}
                             :checked   phonetic?
                             :on-change #(on-phonetic-changed % wrapped-query)}] " Phonetic form"]
                   ^{:key "seg-init"}
                   [segment-initial-checkbox wrapped-query]
                   ^{:key "seg-final"}
                   [segment-final-checkbox wrapped-query]))]]
             (when (:has-headword-search @corpus)
               ^{:key 2}
               [:div.table-row
                [:div.table-cell]
                [:div.table-cell {:style {:padding-left 20}}
                 [headword-search-checkbox wrapped-query]]])))]))

;;; The three different CWB interfaces: simple, extended and cqp

(defn- simple
  "Simple search view component"
  [a m wrapped-query show-remove-row-btn?]
  (let [query           (:query @wrapped-query)
        displayed-query (-> query
                            (->non-headword-query)
                            ;; Unescape any escaped chars, since we don't want the backslashes
                            ;; to show in the text input
                            (str/replace #"\\(.)" "$1")
                            (str/replace #"\[\(?\w+=\"(.*?)\"(?:\s+%c)?\)?\]" "$1")
                            (str/replace #"\"([^\s=]+)\"" "$1")
                            (str/replace #"\s*\[\]\s*" " .* ")
                            (str/replace #"^\s*\.\*\s*$" "")
                            (str/replace "__QUOTE__" "\""))
        on-text-changed (fn [event wrapped-query phonetic?]
                          (let [value (.-target.value event)
                                query (if (= value "") "" (phrase->cqp value phonetic?))]
                            (swap! wrapped-query assoc :query query)))]
    [single-input-view a m "text" wrapped-query displayed-query show-remove-row-btn?
     true on-text-changed]))


(defn- cqp
  "CQP query view component"
  [a m wrapped-query show-remove-row-btn?]
  (let [displayed-query (str/replace (:query @wrapped-query) "__QUOTE__" "\\\"")
        on-text-changed (fn [event wrapped-query _]
                          (let [value      (-> (.-target.value event)
                                               ;; Replace literal quotes with __QUOTE__ to prevent
                                               ;; them from confusing our regexes later on
                                               (str/replace "word=\"\\\"\"" "word=\"__QUOTE__\"")
                                               (str/replace "^\\\"$" "[word=\"__QUOTE__\"]"))
                                query      (->non-headword-query value)
                                hw-search? (= (->headword-query query) value)]
                            (swap! wrapped-query assoc :query query :headword-search hw-search?)))]
    [single-input-view a m "textarea" wrapped-query displayed-query show-remove-row-btn?
     false on-text-changed]))

(defmethod search-inputs :default [_ _]
  "Component that lets the user select a search view (simple, extended
  or CQP query view) and displays it."
  (r/create-class
    {:display-name
     "search-inputs"

     :component-did-mount
     focus-text-input

     :reagent-render
     (fn [{{:keys [view-type queries query-ids]} :search-view :as a} {:keys [corpus] :as m}]
       (let [view          (case @view-type
                             :extended extended
                             :cqp cqp
                             simple)
             languages     (:languages @corpus)
             multilingual? (> (count languages) 1)
             set-view      (fn [view e] (reset! view-type view) (.preventDefault e))]
         [:span
          [:div.row.search-input-links>div.col-sm-12
           (if (= view simple)
             [:b "Simple"]
             [:a {:href     ""
                  :title    "Simple search box"
                  :on-click #(set-view :simple %)}
              "Simple"])
           " | "
           (if (= view extended)
             [:b "Extended"]
             [:a {:href     ""
                  :title    "Search for grammatical categories etc."
                  :on-click #(set-view :extended %)}
              "Extended"])
           " | "
           (if (= view cqp)
             [:b "CQP query"]
             [:a {:href     ""
                  :title    "CQP expressions"
                  :on-click #(set-view :cqp %)}
              "CQP query"])
           [search-button a m (if (= @view-type :extended) 81 233)]]

          ; Now create a cursor into the queries ratom for each search expression
          ; and display a row of search inputs for each of them. The doall call is needed
          ; because ratoms cannot be derefed inside lazy seqs.
          (let [nqueries             (count @queries)
                query-range          (range nqueries)
                show-remove-row-btn? (> nqueries 1)]
            ;; See explanation of query-term-ids in the extended view - query-ids is used
            ;; in the same way, but for queries instead of query terms
            (when (nil? @query-ids)
              (reset! query-ids (vec query-range)))
            (doall (for [index query-range
                         :let [query-id (nth @query-ids index)]]
                     ;; Use wrap rather than cursor to send individual queries down to
                     ;; child components (and in the extended view, we do the same for
                     ;; individual terms). When a query (or query term) changes, the wrap
                     ;; callbacks are called all the way up to the one setting the top-level
                     ;; queries ratom, and all query views (potentially) re-render.
                     ;;
                     ;; By using cursors we could have restricted re-rendering to smaller
                     ;; sub-views, but we need to do some processing of the query (such as
                     ;; changing " .* " to []) before updating it in the queries ratom.
                     ;; We could do this with a getter/setter-style cursor, but then we would
                     ;; have to update the queries ratom anyway, causing the same
                     ;; potential re-rendering of all query views.
                     ;;
                     ;; Probably the most efficient approach would be to use a standard cursor
                     ;; (which only re-renders the view that derefs it) and explicitly call the
                     ;; query processing function before updating the cursor, but then we would
                     ;; have to make sure to do that every time we change a query...
                     (let [wrapped-query (r/wrap
                                           (nth @queries index)
                                           wrapped-query-changed queries
                                           index query-ids)]
                       ^{:key query-id}
                       [:div.row
                        [:div.col-sm-12
                         (when multilingual?
                           [language-select wrapped-query languages queries])
                         [view a m wrapped-query show-remove-row-btn?]]]))))
          (if multilingual?
            [add-language-button a m view]
            [add-phrase-button a m view])
          [show-texts-button a view]]))}))
