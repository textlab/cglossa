(ns cglossa.search-views.cwb.extended.attributes
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.shared :refer [search!]]
            [cglossa.search-views.shared :refer [has-phonetic? has-original?]]
            [cglossa.search-views.cwb.extended.shared :refer [language-data language-menu-data
                                                              language-config
                                                              language-corpus-specific-attrs
                                                              tag-description-label]]
            [clojure.string :as str]))

(defn- pos-panel [wrapped-term menu-data]
  ^{:key "pos-panel"}
  [b/panel {:header "Parts-of-speech"}
   (doall (for [[pos title tooltip] menu-data
                :let [excluded-pos (str "!" pos)
                      selected?    (contains? (:features @wrapped-term) pos)
                      excluded?    (contains? (:features @wrapped-term) excluded-pos)]]
            ^{:key pos}
            [b/button
             {:style       {:margin-left 3 :margin-top 2 :margin-bottom 3}
              :bs-size     "xsmall"
              :bs-style    (cond selected? "info" excluded? "danger" :else "default")
              :data-toggle (when tooltip "tooltip")
              :title       tooltip
              :on-click    (fn [e]
                             ;; Blur button to prevent tooltip from sticking
                             (.blur (js/$ (.-target e)))
                             (if (.-shiftKey e)
                               ;; On shift-click, exclude this pos
                               (swap! wrapped-term update :features
                                      (fn [poses]
                                        (cond
                                          excluded?
                                          ;; If the pos was already excluded, shift-click removes
                                          ;; that state (as does a click without shift - see below).
                                          (dissoc poses excluded-pos)

                                          :else
                                          ;; If we say that we want to exclude this pos, remove
                                          ;; any previously selected poses, since it would be
                                          ;; redundant to exclude this pos if we kept the
                                          ;; selected ones. For instance, if we say that something
                                          ;; should be either a noun or a pronoun, there is no
                                          ;; need to also specify that it should not be a verb.
                                          ;; So if the user now wants to exclude verbs, assume that
                                          ;; she no longer wants any previous selections.
                                          (as-> poses $
                                                (filter (fn [[k v]]
                                                          (str/starts-with? k "!")) $)
                                                (into {} $)
                                                (assoc $ excluded-pos {})))))
                               ;; No shift key. If this pos has already been selected or
                               ;; excluded, remove it from that state. Otherwise, select it
                               ;; and remove any previously excluded poses, since they become
                               ;; redundant - see comment above.
                               (swap! wrapped-term update :features
                                      (fn [poses]
                                        (cond
                                          selected?
                                          (dissoc poses pos)

                                          excluded?
                                          (dissoc poses excluded-pos)

                                          :else
                                          (as-> poses $
                                                (remove (fn [[k v]]
                                                          (str/starts-with? k "!")) $)
                                                (into {} $)
                                                (assoc $ pos {})))))))}
             (or title pos)]))])

(defn- morphsyn-panel [wrapped-term menu-data]
  (for [[pos title _ morphsyn] menu-data
        :when (and (contains? (:features @wrapped-term) pos)
                   (seq morphsyn))]
    ^{:key pos}
    [b/panel {:header (str "Morphosyntactic features for " (or title pos))}
     [:div.table-display
      (for [[header attrs] (partition 2 morphsyn)]
        ^{:key header}
        [:div.table-row
         [:div.table-cell header ": "]
         [:div.table-cell {:style {:padding-bottom 5}}
          (doall (for [[attr value title] attrs
                       :let [attr*     (name attr)
                             selected? (contains? (get-in @wrapped-term
                                                          [:features pos attr*])
                                                  value)]]
                   ^{:key value}
                   [b/button {:style    {:margin-left 3 :margin-bottom 5}
                              :bs-size  "xsmall"
                              :bs-style (if selected? "info" "default")
                              :on-click (fn [e]
                                          ;; Blur button to prevent tooltip from sticking
                                          (.blur (js/$ (.-target e)))
                                          (swap! wrapped-term
                                                 update-in [:features pos attr*]
                                                 (fn [a] (if selected?
                                                           (disj a value)
                                                           (set (conj a value)))))
                                          (when (empty? (get-in @wrapped-term
                                                                [:features pos attr*]))
                                            (swap! wrapped-term
                                                   update-in [:features pos]
                                                   dissoc attr*)))}
                    (or title value)]))]])]]))

(defn- corpus-specific-panel [corpus wrapped-query wrapped-term]
  (when-let [selected-language (:lang @wrapped-query)]
    (when-let [attr-specs (language-corpus-specific-attrs corpus selected-language)]
      (for [[attr header & attr-values] attr-specs]
        ^{:key (str "csa-" (name attr))}
        [b/panel {:header header}
         (doall (for [[attr-value title tooltip] attr-values
                      :let [attr*     (name attr)
                            selected? (contains? (get-in @wrapped-term
                                                         [:corpus-specific-attrs attr*])
                                                 attr-value)]]
                  ^{:key attr-value}
                  [b/button
                   {:style       {:margin-left 3 :margin-top 2 :margin-bottom 3}
                    :bs-size     "xsmall"
                    :bs-style    (if selected? "info" "default")
                    :data-toggle (when tooltip "tooltip")
                    :title       tooltip
                    :on-click    (fn [e]
                                   ;; Blur button to prevent tooltip from sticking
                                   (.blur (js/$ (.-target e)))
                                   (swap! wrapped-term
                                          update-in [:corpus-specific-attrs attr*]
                                          (fn [a] (if selected?
                                                    (disj a attr-value)
                                                    (set (conj a attr-value)))))
                                   (when (empty? (get-in @wrapped-term
                                                         [:corpus-specific-attrs attr*]))
                                     (swap! wrapped-term
                                            update :corpus-specific-attrs
                                            dissoc attr*)))}
                   (or title (str/capitalize attr-value))]))]))))

(defn- additional-words-panel [corpus wrapped-query wrapped-term show-attr-popup?]
  (r/with-let [text (r/atom "")]
    (let [on-select (fn [event-key _]
                      (let [[key val] (if (str/starts-with? event-key "!")
                                        ;; Move the exclamation point from the key to the value
                                        [(subs event-key 1) (str "!" @text)]
                                        [event-key @text])]
                        (swap! wrapped-term update-in [:extra-forms key]
                               (fn [vals]
                                 (if vals
                                   (conj vals val)
                                   (set [val]))))))
          items     {:add-word      [b/menuitem {:key       :add-word
                                                 :event-key "word"
                                                 :on-select on-select}
                                     "Add word form"]
                     :add-lemma     [b/menuitem {:key       :add-lemma
                                                 :event-key "lemma"
                                                 :on-select on-select}
                                     "Add lemma"]
                     :add-phon      [b/menuitem {:key       :add-phon
                                                 :event-key "phon"
                                                 :on-select on-select}
                                     "Add phonetic form"]
                     :add-orig      [b/menuitem {:key       :add-orig
                                                 :event-key "orig"
                                                 :on-select on-select}
                                     "Add original form"]
                     :exclude-word  [b/menuitem {:key       :exclude-word
                                                 :event-key "!word"
                                                 :on-select on-select}
                                     "Exclude word form"]
                     :exclude-lemma [b/menuitem {:key       :exclude-lemma
                                                 :event-key "!lemma"
                                                 :on-select on-select}
                                     "Exclude lemma"]
                     :exclude-phon  [b/menuitem {:key       :exclude-phon
                                                 :event-key "!phon"
                                                 :on-select on-select}
                                     "Exclude phonetic form"]
                     :exclude-orig  [b/menuitem {:key       :exclude-orig
                                                 :event-key "!orig"
                                                 :on-select on-select}
                                     "Exclude original form"]}
          sel-items (cond
                      (has-phonetic? @corpus)
                      (select-keys items [:add-word :add-lemma :add-phon
                                          :exclude-word :exclude-lemma :exclude-phon])

                      (has-original? @corpus)
                      (select-keys items [:add-word :add-lemma :add-orig
                                          :exclude-word :exclude-lemma :exclude-orig])

                      :else
                      (select-keys items [:add-word :add-lemma
                                          :exclude-word :exclude-lemma]))]
      ^{:key (str "additional-words")}
      [b/panel
       [:div {:style {:display "table"}}
        [:div {:style {:display "table-cell"}}
         [b/inputgroup {:bs-size "small" :style {:width 250}}
          [b/formcontrol {:type "text" :value @text :on-change #(reset! text (.-target.value %))}]
          [b/dropdownbutton {:component-class js/ReactBootstrap.InputGroup.Button
                             :bs-size         "small"
                             :id              "additional-word"
                             :title           "Add or exclude"
                             :disabled        (str/blank? @text)}
           (vals sel-items)]]]
        [:div {:style {:display "table-cell" :padding-left 10}}
         (for [[attr forms] (:extra-forms @wrapped-term)
               form forms
               :let [description (if (= attr "word")
                                   form
                                   (str attr ":" form))]]
           ^{:key (str attr "_" form)}
           [tag-description-label form description "" [:extra-forms attr]
            wrapped-term show-attr-popup?])]]])))

(defn- attribute-modal [a {:keys [corpus] :as m}
                        wrapped-query wrapped-term menu-data show-attr-popup?]
  [b/modal {:class-name "attr-modal"
            :bs-size    "large"
            :keyboard   true
            :show       @show-attr-popup?
            :on-hide    #(reset! show-attr-popup? false)}
   [b/modalbody
    (list
      (when menu-data (pos-panel wrapped-term menu-data))
      (when menu-data (morphsyn-panel wrapped-term menu-data))
      (corpus-specific-panel corpus wrapped-query wrapped-term)
      (additional-words-panel corpus wrapped-query wrapped-term show-attr-popup?))]
   [b/modalfooter {:style {:position "relative"}}
    [:div {:style {:height 30}}]
    [:div {:style {:position  "absolute"
                   :display   "inline-block"
                   :top       15
                   :left      17
                   :font-size 13
                   :color     "#4E4E4E"}}
     "Click to select; shift-click to exclude"]
    [:div {:style {:position "absolute" :display "inline-block" :top 8 :right 15}}
     [b/button {:bs-style "danger"
                :on-click #(swap! wrapped-term assoc :features nil)} "Clear"]
     [b/button {:bs-style "success"
                :on-click (fn [_] (reset! show-attr-popup? false) (search! a m))} "Search"]
     [b/button {:bs-style "info"
                :on-click #(reset! show-attr-popup? false)} "Close"]]]])


(defn menu-button [a {:keys [corpus] :as m}
                   wrapped-query wrapped-term index show-attr-popup?]
  (r/with-let [options-clicked (atom false)]
    (let [selected-language (:lang @wrapped-query)
          menu-data         (language-menu-data corpus selected-language)]
      (list
        ^{:key "btn"}
        [b/dropdown {:id    (str "search-term-pos-dropdown-" index)
                     :style {:width 59}}
         [b/button {:bs-size  "small"
                    :on-click #(reset! show-attr-popup? true)}
          [b/glyphicon {:glyph "list"}]]
         [b/dropdown-toggle {:bs-size "small" :disabled (nil? menu-data)}]
         [b/dropdown-menu {:style {:min-width 180}}
          (for [[pos title tooltip] menu-data
                :let [selected? (contains? (:features @wrapped-term) pos)]]
            ^{:key pos}
            [b/menuitem {:active      selected?
                         :data-toggle (when tooltip "tooltip")
                         :title       tooltip
                         :on-click    (fn [_]
                                        (if selected?
                                          ;; Deselect the part-of-speech if it was
                                          ;; selected AND we didn't click the options
                                          ;; icon (because in that case we want to
                                          ;; specify morphosyntactic features, not
                                          ;; deselect).
                                          (when-not @options-clicked
                                            (swap! wrapped-term update :features
                                                   dissoc pos))
                                          ;; If it was not selected, we select it
                                          ;; regardless of whether or not the options
                                          ;; icon was clicked.
                                          (swap! wrapped-term assoc-in [:features pos]
                                                 {}))
                                        (reset! options-clicked false))}
             (or title pos) [b/glyphicon
                             {:glyph    "option-horizontal"
                              :title    "More options"
                              :style    {:float "right" :margin-left 5 :margin-top 3}
                              :on-click (fn [e]
                                          ;; Clicking the option icon on a menu item both
                                          ;; selects the part-of-speech and opens the
                                          ;; popup for advanced attributes
                                          (.preventDefault e)
                                          ;; Set a flag to make sure we don't deselect
                                          ;; this part-of-speech when the event bubbles
                                          ;; up to the menu item if it was already
                                          ;; selected
                                          (reset! options-clicked true)
                                          (reset! show-attr-popup? true))}]])]]
        ^{:key "modal"}
        [attribute-modal a m wrapped-query wrapped-term menu-data show-attr-popup?]))))
