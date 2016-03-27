(ns cglossa.search-views.cwb.extended.attributes
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.shared :refer [search!]]
            [cglossa.search-views.cwb.extended.shared :refer [language-data language-menu-data
                                                              language-config
                                                              language-corpus-specific-attrs]]))

(defn- pos-panel [wrapped-term menu-data]
  ^{:key "pos-panel"}
  [b/panel {:header "Parts-of-speech"}
   (doall (for [[pos title tooltip] menu-data
                :let [selected? (contains? (:features @wrapped-term) pos)]]
            ^{:key pos}
            [b/button
             {:style       {:margin-left 3 :margin-top 2 :margin-bottom 3}
              :bs-size     "xsmall"
              :bs-style    (if selected? "info" "default")
              :data-toggle (when tooltip "tooltip")
              :title       tooltip
              :on-click    (fn [e]
                             ;; Blur button to prevent tooltip from sticking
                             (.blur (js/$ (.-target e)))
                             (swap! wrapped-term update :features
                                    #(if selected?
                                      (dissoc % pos)
                                      (assoc % pos {}))))}
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
                   (or title attr-value)]))]))))

(defn- attribute-modal [a {:keys [corpus] :as m}
                        wrapped-query wrapped-term menu-data show-attr-popup?]
  [b/modal {:class-name "attr-modal"
            :bs-size    "large"
            :keyboard   true
            :show       @show-attr-popup?
            :on-hide    #(reset! show-attr-popup? false)}
   [b/modalbody
    (when menu-data
      (list
        (pos-panel wrapped-term menu-data)
        (morphsyn-panel wrapped-term menu-data)
        (corpus-specific-panel corpus wrapped-query wrapped-term)))]
   [b/modalfooter
    [b/button {:bs-style "danger"
               :on-click #(swap! wrapped-term assoc :features nil)} "Clear"]
    [b/button {:bs-style "success"
               :on-click (fn [_] (reset! show-attr-popup? false) (search! a m))} "Search"]
    [b/button {:bs-style "info"
               :on-click #(reset! show-attr-popup? false)} "Close"]]])


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
