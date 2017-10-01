(ns cglossa.corpora.norm
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

(defmethod show-texts-extra-col-comp "norm" [corpus m]
  (let [on-click
        (fn [row-data e]
          (.preventDefault e)
          (let [target  (.-target e)
                text-id (get (js->clj row-data) "Tekst-ID")]
            (go
              (let [{:keys [body]} (<! (http/get (str (:code @corpus) "/result-metadata")
                                                 {:query-params {:text-id text-id}}))
                    ; Show all categories in the popup
                    ;cats (remove #(str/starts-with? (name (:code %)) "hd_") @metadata-categories)
                    cats      @(:metadata-categories m)
                    cat-names (into {} (map (fn [{:keys [id code name]}]
                                              (let [name* (or name (-> code
                                                                       (str/replace "_" " ")
                                                                       str/capitalize))]
                                                [id name*]))
                                            cats))
                    vals      (keep (fn [{:keys [metadata_category_id text_value]}]
                                      (when-let [name (get cat-names metadata_category_id)]
                                        [name text_value]))
                                    body)
                    metadata  {:node target :vals vals}]
                (reset! line-showing-metadata metadata)))))]
    (r/create-class
      {:on-click (fn [_] (reset! line-showing-metadata nil))
       :render   (fn [this]
                   [:span
                    [:div
                     [:a {:href     ""
                          :on-click (partial on-click (:rowData (r/props this)))}
                      [b/glyphicon {:glyph "info-sign"}]]
                     (metadata-overlay line-showing-metadata)]
                    (let [text-id (get (js->clj (:rowData (r/props this))) "Tekst-ID")]
                      [:a {:href   (str "http://tekstlab.uio.no/norm/oppgavesvar/" text-id ".pdf")
                           :target "_blank"}
                       [b/glyphicon {:glyph "file"}]])])})))
