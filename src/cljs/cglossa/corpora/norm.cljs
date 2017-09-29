(ns cglossa.corpora.norm
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links show-texts-extra-col-name
                                     show-texts-extra-col-comp]
             cglossa.result-views.cwb.shared :refer [get-result-metadata metadata-overlay]]))

(defmethod result-links "norm" [_ _ result _]
  (let [text-id  (re-find #".+(?=\.)" (:s-id result))
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
                :title   "Oppgavesvar" :on-click on-click
                :href    (str "http://tekstlab.uio.no/norm/oppgavesvar/" text-id ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]]))

(defmethod show-texts-extra-col-name "norm" [_]
  {:code "extra-col" :name "Oppgavesvar"})

(defmethod show-texts-extra-col-comp "norm" [_]
  (r/create-class
    {:render
     (fn [this]
       [:div
        [:a {:href     ""
             :on-click #(get-result-metadata % result-showing-metadata metadata-categories
                                             (:code @corpus) text-id result-hash)}
         [:span {:id result-hash} s-id]]
        (metadata-overlay result-showing-metadata)
        [result-links a m result row-index]]
       (let [text-id (get (js->clj (:rowData (r/props this))) "Tekst-ID")]
         [:a {:href   (str "http://tekstlab.uio.no/norm/oppgavesvar/" text-id ".pdf")
              :target "_blank"}
          [b/glyphicon {:glyph "file"}]]))}))
