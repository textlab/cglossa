(ns cglossa.corpora.norint-tekst
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links show-texts-extra-col-name
                                     show-texts-extra-col-comp]]))

(defmethod result-links "norint_tekst" [_ _ result _]
  (let [text-id  (re-find #"^\d+" (:s-id result))
        on-click (fn [e]
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
    [:div {:style {:margin-top 5 :text-align "center"}}
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style   {:color "#666" :font-size 10}
                :title   "Besvarelse" :on-click on-click
                :href    (str "http://tekstlab.uio.no/norint_tekst/"
                              text-id "/" text-id ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]]))

(defmethod show-texts-extra-col-name "norint_tekst" [_]
  {:code "extra-col" :name "Besvarelse"})

(defmethod show-texts-extra-col-comp "norint_tekst" [_ _]
  (r/create-class
    {:render
     (fn [this]
       (let [text-id (get (js->clj (:rowData (r/props this))) "Informantnummer")]
         [:a {:href   (str "http://tekstlab.uio.no/norint_tekst/"
                           text-id "/" text-id ".pdf")
              :target "_blank"}
          [b/glyphicon {:glyph "file"}]]))}))
