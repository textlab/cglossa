(ns cglossa.corpora.trawl
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links show-texts-extra-col-name
                                     show-texts-extra-col-comp]]
            [cglossa.result-views.cwb.shared :refer [line-showing-metadata
                                                     get-result-metadata metadata-overlay]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod result-links "trawl" [_ _ result _]
  (let [assignment-id (second (re-find #"^.+?_(.+)_" (:s-id result)))
        paper-id      (second (re-find #"(.+)\.s\d" (:s-id result)))
        comm-paper-id (str/replace paper-id "ORIG" "COMM")
        on-click      (fn [e]
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
                :style   {:color "orange" :font-size 10}
                :title   "Assignment text" :on-click on-click
                :href    (str "http://tekstlab.uio.no/trawl/oppgavetekster/" assignment-id ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style   {:color "green" :font-size 10 :margin-left 3}
                :title   "Student paper" :on-click on-click
                :href    (str "http://tekstlab.uio.no/trawl/oppgavesvar/" paper-id ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]
     [b/button {:bs-size "xsmall" :data-toggle "tooltip"
                :style   {:color "green" :font-size 10 :margin-left 3}
                :title   "Student paper with teacher comments" :on-click on-click
                :href    (str "http://tekstlab.uio.no/trawl/oppgavesvar_m_komm/" comm-paper-id ".pdf")
                :target  "_blank"}
      [b/glyphicon {:glyph "file"}]]]))
