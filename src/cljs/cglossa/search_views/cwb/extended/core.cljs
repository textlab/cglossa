(ns cglossa.search-views.cwb.extended.core
  "Implementation of search view component with text inputs, checkboxes
  and menus for easily building complex and grammatically specified queries."
  (:require [clojure.string :as str]
            [reagent.core :as r :include-macros true]
            [cglossa.shared :refer [on-key-down remove-row-btn headword-search-checkbox
                                    segment-initial-checkbox segment-final-checkbox]]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.search-views.shared :refer [has-phonetic?]]
            [cglossa.search-views.cwb.extended.shared :refer [language-data language-menu-data
                                                              language-config
                                                              language-corpus-specific-attrs]]
            [cglossa.search-views.cwb.extended.cqp :refer [set-pos-attr query->terms
                                                           terms->query]]
            [cglossa.search-views.cwb.extended.attributes :refer [menu-button]]))

(defn wrapped-term-changed [wrapped-query terms index query-term-ids lang-config term]
  (swap! wrapped-query assoc :query (terms->query wrapped-query (assoc terms index term)
                                                  query-term-ids lang-config)))

;;;;;;;;;;;;;;;;
;;;; Components
;;;;;;;;;;;;;;;;

(defn- text-input [a m wrapped-query wrapped-term index show-remove-term-btn? show-attr-popup?]
  [:div.table-cell
   [b/inputgroup
    [b/inputgroup-button (menu-button a m wrapped-query wrapped-term index
                                      show-attr-popup?)]
    [b/formcontrol
     {:type          "text"
      :bs-size       "small"
      :style         {:font-size 14
                      :width     108
                      :height    30}
      :default-value (if (:form @wrapped-term)
                       (-> (:form @wrapped-term)
                           (str/replace "__QUOTE__" "\"")
                           (str/replace #"^\.\*$" ""))
                       "")
      :on-change     #(swap! wrapped-term assoc :form
                             ;; Replace literal quotes with __QUOTE__ to prevent them from
                             ;; confusing our regexes later on
                             (str/replace (.-target.value %)
                                          "\"" "__QUOTE__"))
      :on-key-down   #(on-key-down % a m)}]
    (when show-remove-term-btn?
      [b/inputgroup-button
       [b/button {:bs-size  "small"
                  :title    "Remove search word"
                  :on-click #(reset! wrapped-term nil)}
        [b/glyphicon {:glyph "minus"}]]])]])


(defn- add-term-btn [wrapped-query wrapped-term query-term-ids]
  [:div.table-cell {:style {:vertical-align "bottom" :padding-left 14 :padding-bottom 5}}
   [b/button {:bs-style "info"
              :bs-size  "xsmall"
              :title    "Add search word"
              :disabled (and (str/blank? (:form @wrapped-term))
                             (nil? (:features @wrapped-term))
                             (nil? (:corpus-specific-attrs @wrapped-term)))
              :on-click (fn []
                          ;; Append greatest-current-id-plus-one to the
                          ;; query-term-ids vector
                          (swap! query-term-ids
                                 #(conj % (inc (apply max %))))
                          ;; Append [] to the end of the CQP query expression (but before
                          ;; </sync>, if present)
                          (swap! wrapped-query
                                 update :query str/replace #"(.+?)(</sync>)?$" "$1 []$2"))}
    [b/glyphicon {:glyph "plus"}]]])


(defn- interval-input [a m wrapped-term index]
  [b/formcontrol
   {:type        "text"
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


(defn- checkboxes [wrapped-query wrapped-term has-phon? first? last?]
  (let [term-val @wrapped-term]
    [:div.table-cell {:style {:min-width 200}}
     [:div.word-checkboxes
      [:label.checkbox-inline {:style {:padding-left 18}}
       [:input {:type      "checkbox"
                :style     {:margin-left -18}
                :checked   (:lemma? term-val)
                :on-change #(swap! wrapped-term assoc :lemma? (.-target.checked %))
                }] "Lemma"]
      [:label.checkbox-inline {:style {:padding-left 26}}
       [:input {:type      "checkbox"
                :style     {:margin-left -18}
                :title     "Start of word"
                :checked   (:start? term-val)
                :on-change #(swap! wrapped-term assoc :start? (.-target.checked %))
                }] "Start"]
      [:label.checkbox-inline {:style {:padding-left 23}}
       [:input {:type      "checkbox"
                :style     {:margin-left -18}
                :title     "End of word"
                :checked   (:end? term-val)
                :on-change #(swap! wrapped-term assoc :end? (.-target.checked %))
                }] "End"]]
     (when has-phon?
       [:div
        [:label.checkbox-inline {:style {:padding-left 18}}
         [:input {:type      "checkbox"
                  :style     {:margin-left -18}
                  :checked   (:phonetic? term-val)
                  :on-change #(swap! wrapped-term assoc :phonetic? (.-target.checked %))
                  }] "Phonetic"]
        (if (and first? last?)
          [:div
           [segment-initial-checkbox wrapped-query]
           [segment-final-checkbox wrapped-query]]
          (list (when first? ^{:key "seg-init"} [segment-initial-checkbox wrapped-query])
                (when last? ^{:key "seg-final"} [segment-final-checkbox wrapped-query])))])]))


(defn- tag-descriptions [data wrapped-term path]
  ;; The comments and binding names below reflect the case where the tags to be described are
  ;; part-of-speech tags along with values for morphosyntactic features for the POS, but this
  ;; function is also used for constructing descriptions of corpus-specific attributes.
  ;; In that case, the `morphsyn` binding in the for loop will be nil, and hence cat-description
  ;; will not be called, since such attributes don't have nested data.
  (let [;; Returns a description of the selected morphosyntactic features for a particular
        ;; morphosyntactic category. 'attrs' is a seq of possible values for this category, with
        ;; each value represented as a vector of [cqp-attribute short-value human-readable-value]
        ;; (short-value is e.g. "pcp1" while human-readable-value is e.g. "present participle").
        cat-description (fn [pos attrs]
                          (str/join " or "
                                    (->> attrs
                                         (filter (fn [[attr value _]]
                                                   (contains? (get-in @wrapped-term
                                                                      (conj path pos (name attr)))
                                                              value)))
                                         ;; Get human-readable value
                                         (map last))))]
    (for [[pos pos-title tooltip morphsyn] data
          ;; Only consider parts-of-speech that have actually been selected
          :when (contains? (get-in @wrapped-term path) pos)
          ;; Extract the seq of possible morphosyntactic features for each morphosyntacic category
          ;; that applies to this part-of-speech
          :let [cat-attrs (->> morphsyn (partition 2) (map second))]]
      {:pos         pos
       :description (str (if pos-title (str/capitalize pos-title) pos) " "
                         (str/join " " (map (partial cat-description pos) cat-attrs)))
       :tooltip     tooltip})))


(defn- tag-description-label [value description tooltip path wrapped-term show-attr-popup?]
  [b/label {:bs-style    "primary"
            :data-toggle (when tooltip "tooltip")
            :title       tooltip
            :style       {:float        "left"
                          :margin-top   3
                          :margin-right 3
                          :cursor       "pointer"}
            :on-click    #(reset! show-attr-popup? true)}
   description [:span {:style    {:margin-left 6 :cursor "pointer"}
                       :on-click (fn [e]
                                   (.stopPropagation e)
                                   ;; Need to manually remove the tooltip of our parent
                                   ;; label; otherwise the tooltip may persist after
                                   ;; the label has been removed (and then there is no way
                                   ;; to remove it).
                                   (-> (.-target e)
                                       js/$
                                       (.closest "[data-toggle]")
                                       (.tooltip "destroy"))
                                   (swap! wrapped-term update-in path (fn [o]
                                                                        (if (map? o)
                                                                          (dissoc o value)
                                                                          (disj o value))))
                                   (when (empty? (get-in @wrapped-term path))
                                     (let [path*     (butlast path)
                                           attr-name (last path)]
                                       (swap! wrapped-term update-in path* dissoc attr-name))))}
                "x"]])


(defn- taglist [{:keys [corpus]} wrapped-term lang-code show-attr-popup?]
  ;; Ideally, hovering? should be initialized to true if the mouse is already hovering over the
  ;; component when it is mounted, but that seems tricky. For the time being, we accept the
  ;; fact that we have to mouse out and then in again if we were already hovering.
  (r/with-let [hovering? (r/atom false)]
    (let [menu-data             (language-menu-data corpus lang-code)
          descriptions          (tag-descriptions menu-data wrapped-term [:features])
          corpus-specific-attrs (language-corpus-specific-attrs corpus lang-code)
          cs-names              (map first corpus-specific-attrs)
          cs-vals               (map nnext corpus-specific-attrs)
          cs-descriptions       (map (fn [tag-name vals]
                                       (tag-descriptions vals wrapped-term
                                                         [:corpus-specific-attrs
                                                          (name tag-name)]))
                                     cs-names cs-vals)]
      [:div.table-cell {:style          {:max-width  200
                                         ;; Show the descriptions in their entire length on
                                         ;; mouseover
                                         :overflow-x (if @hovering? "visible" "hidden")}
                        :on-mouse-enter #(reset! hovering? true)
                        :on-mouse-leave #(reset! hovering? false)}
       [:div {:style {:margin-top 5}}
        (for [{:keys [pos description tooltip]} descriptions]
          ^{:key description}
          [tag-description-label pos description tooltip [:features]
           wrapped-term show-attr-popup?])
        (map (fn [attr desc]
               (for [{:keys [pos description tooltip]} desc]
                 ^{:key description}
                 [tag-description-label pos description tooltip [:corpus-specific-attrs (name attr)]
                  wrapped-term show-attr-popup?]))
             cs-names cs-descriptions)]])))


(defn multiword-term [a m wrapped-query wrapped-term query-term-ids
                      index first? last? has-phon? show-remove-row-btn?
                      show-remove-term-btn?]
  (r/with-let [show-attr-popup? (r/atom false)]
    [:div.table-cell>div.multiword-term>div.control-group
     [:div.table-row
      (when first?
        [remove-row-btn show-remove-row-btn? wrapped-query])
      [text-input a m wrapped-query wrapped-term index show-remove-term-btn? show-attr-popup?]
      (when last?
        [add-term-btn wrapped-query wrapped-term query-term-ids])]

     [:div.table-row
      (when first?
        [:div.table-cell])
      [checkboxes wrapped-query wrapped-term has-phon? first? last?]
      (when last?
        [:div.table-cell])]

     [:div.table-row {:style {:margin-top 5}}
      (when first?
        [:div.table-cell])
      [taglist m wrapped-term (:lang @wrapped-query) show-attr-popup?]
      (when last?
        [:div.table-cell])]]))


(defn extended
  "Search view component with text inputs, checkboxes and menus
  for easily building complex and grammatically specified queries."
  [a {:keys [corpus] :as m} wrapped-query show-remove-row-btn?]
  (r/with-let [;; This will hold a unique ID for each query term component. React wants a
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
    (let [lang-code             (:lang @wrapped-query)
          lang-config           (language-config corpus lang-code)
          query                 (set-pos-attr wrapped-query lang-config)
          corpus-specific-attrs (->> (language-corpus-specific-attrs corpus lang-code)
                                     (map first)
                                     (map name))
          terms                 (query->terms query corpus-specific-attrs)
          last-term-index       (dec (count terms))]
      (when (nil? @query-term-ids)
        (reset! query-term-ids (vec (range (count terms)))))
      [:div.multiword-container
       [:form.form-inline.multiword-search-form {:style {:margin-left -35}}
        [:div.table-display
         [:div.table-row
          (doall
            (map-indexed (fn [index term]
                           (let [wrapped-term          (r/wrap term
                                                               wrapped-term-changed
                                                               wrapped-query terms index
                                                               query-term-ids lang-config)
                                 term-id               (nth @query-term-ids index)
                                 first?                (zero? index)
                                 last?                 (= index last-term-index)
                                 ;; Show buttons to remove terms if there is more than one term
                                 show-remove-term-btn? (pos? last-term-index)]
                             (list (when-not first?
                                     ^{:key (str "interval" term-id)}
                                     [interval a m wrapped-term corpus])
                                   ^{:key (str "term" term-id)}
                                   [multiword-term a m wrapped-query wrapped-term query-term-ids
                                    index first? last? (has-phonetic? @corpus) show-remove-row-btn?
                                    show-remove-term-btn?])))
                         terms))]
         (when (:has-headword-search @corpus)
           [:div.table-row
            [:div.table-cell {:style {:padding-left 40 :padding-top 10}}
             [headword-search-checkbox wrapped-query]]])]]])))
