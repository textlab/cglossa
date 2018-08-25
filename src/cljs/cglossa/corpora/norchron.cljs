(ns cglossa.corpora.norchron
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private show-document (r/atom nil))
(def ^:private row-index-shown (r/atom nil))

(defn- document-popup []
  (r/with-let [hide (fn []
                      (reset! show-document nil)
                      (reset! row-index-shown nil))
               text-id (re-find #"^[^\.]+" @show-document)
               modal-body (r/atom nil)
               views (r/atom [:dipl])
               on-view-change (fn [vals]
                                (reset! views vals)
                                (let [facs (js/$ ".norchron-doc-facs")
                                      dipl (js/$ ".norchron-doc-dipl")
                                      norm (js/$ ".norchron-doc-norm")
                                      img  (js/$ ".norchron-doc-img")]
                                  (if (some #(= "facs" %) vals)
                                    (.css (js/$ facs) "display" "table-cell")
                                    (.hide (js/$ facs)))
                                  (if (some #(= "dipl" %) vals)
                                    (.css (js/$ dipl) "display" "table-cell")
                                    (.hide (js/$ dipl)))
                                  (if (some #(= "norm" %) vals)
                                    (.css (js/$ norm) "display" "table-cell")
                                    (.hide (js/$ norm)))
                                  (if (some #(= "img" %) vals)
                                    (.css (js/$ img) "display" "table-cell")
                                    (.hide (js/$ img)))))]
    (go (let [{:keys [body]} (<! (http/get (str "/norchron/data/"
                                                text-id
                                                ".xml.html")))]
          (reset! modal-body body)))
    [:div.show-norchron-document-popup
     [b/modal {:show              true
               :on-hide           hide
               :dialog-class-name "show-norchron-document-popup"}
      [b/modalheader {:close-button true}
       [b/modaltitle (str "Document " text-id)]]
      [b/modalbody [:span
                    [b/buttontoolbar
                     [b/togglebuttongroup
                      {:bs-size   "small"
                       :type      "checkbox"
                       :value     @views
                       :on-change on-view-change}
                      (when (and @modal-body (str/includes? @modal-body "norchron-doc-facs"))
                        [b/togglebutton {:value "facs"} "Facsimile"])
                      (when (and @modal-body (str/includes? @modal-body "norchron-doc-dipl"))
                        [b/togglebutton {:value "dipl"} "Diplomatic"])
                      (when (and @modal-body (str/includes? @modal-body "norchron-doc-norm"))
                        [b/togglebutton {:value "norm"} "Normalized"])
                      [b/togglebutton {:value "img"} "Photographic facsimile"]]]
                    [:div {:dangerouslySetInnerHTML {:__html @modal-body}}]]]
      [b/modalfooter
       [b/button {:on-click hide} "Close"]]]]))

(defmethod result-links "norchron" [_ _ result row-index]
  (let [s-id     (:s-id result)
        on-click (fn [e]
                   (reset! show-document s-id)
                   (reset! row-index-shown row-index)
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
     (when (= @row-index-shown row-index)
       (document-popup))
     [:div {:style {:margin-top 5 :text-align "center"}}
      [b/button {:bs-size  "xsmall" :data-toggle "tooltip"
                 :style    {:color "#666" :font-size 10}
                 :title    "Document"
                 :on-click on-click}
       [b/glyphicon {:glyph "file"}]]]]))

