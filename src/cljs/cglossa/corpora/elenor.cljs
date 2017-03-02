(ns cglossa.corpora.elenor
  (:require [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]]))

(defmethod result-links "elenor" [_ _ result _]
  (let [on-click (fn [e]
                   ;; Need to manually remove the button tooltip; otherwise it
                   ;; may persist after the document opens in a different tab/window
                   ;; (and then there is no way to remove it). We also need to blur
                   ;; the button in order to prevent the tooltip from reappearing and
                   ;; persisting when we return to the window.
                   (-> (.-target e)
                       js/$
                       (.closest "[data-toggle]")
                       (.blur)
                       (.tooltip "destroy")))]
    [:div {:style {:margin-top 5}}
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style {:color "#666" :font-size 10}
                :title   "Oppgavetekst" :on-click on-click
                :href    (str "http://www.tekstlab.uio.no/elenor/pdf/1_Oppgavetekst/"
                              (second (re-find #"_(.+)_(?:KORR|ORG)\.s\d+$" (:s-id result))) ".pdf ")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style {:color "#666" :font-size 10 :margin-left 3}
                :title   "Spørreskjema" :on-click on-click
                :href    (str "http://www.tekstlab.uio.no/elenor/pdf/2_Spørreskjema/"
                              (re-find #"^.+?(?=_)" (:s-id result)) ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style {:color "#666" :font-size 10 :margin-left 3}
                :title   "Besvarelse" :on-click on-click
                :href    (str "http://www.tekstlab.uio.no/elenor/pdf/3_Oppgaver/"
                              (re-find #"^.+(?=_)" (:s-id result)) ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]]))
