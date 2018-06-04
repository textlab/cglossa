(ns cglossa.corpora.norchron
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private show-document (r/atom nil))

(defn- document-popup []
  (let [hide    #(reset! show-document nil)
        text-id (re-find #"^[^\.]+" @show-document)
        {:keys [body]} (go (<! (http/get (str "/norchron/docs/"
                                              text-id
                                              ".xml.html"))))]
    [:div.show-norchron-document-popup
     [b/modal {:show              true
               :on-hide           hide
               :dialog-class-name "show-texts-popup"}
      [b/modalheader {:close-button true}
       [b/modaltitle (str "Document " text-id)]]
      [b/modalbody [:div "hei"]]
      [b/modalfooter
       [b/button {:on-click hide} "Close"]]]]))

(defmethod result-links "norchron" [_ _ result _]
  (let [s-id     (:s-id result)
        on-click (fn [e]
                   (reset! show-document s-id)
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
    [:span
     (when (= @show-document s-id)
       (document-popup))
     [:div {:style {:margin-top 5 :text-align "center"}}
      [b/button {:bs-size  "xsmall" :data-toggle "tooltip"
                 :style    {:color "#666" :font-size 10}
                 :title    "Document"
                 :on-click on-click}
       [b/glyphicon {:glyph "file"}]]]]))

