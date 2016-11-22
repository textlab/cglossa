(ns cglossa.show-texts
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [cljs-http.client :as http]
            griddle
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.metadata-list :refer [text-selection]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import [goog.dom ViewportSizeMonitor]))

(defn- category-name [{:keys [code name]}]
  (or name (-> code (str/replace "_" " ") str/capitalize)))

(defn- get-external-data [{:keys [corpus metadata-categories search]}
                          results loading? mxpages cur-page page]
  (reset! loading? true)
  (go (let [response (<! (http/post "/texts" {:json-params
                                              {:corpus-id         (:id @corpus)
                                               :selected-metadata (:metadata @search)
                                               :ncats             (count @metadata-categories)
                                               :page              page}}))
            {:keys [rows max-pages] :as body} (:body response)]
        (if (http/unexceptional-status? (:status response))
          (let [rows* (for [row rows]
                        (into {} (map (fn [cat]
                                        [(category-name cat) (get row (:id cat))])
                                      @metadata-categories)))]
            (reset! loading? false)
            (swap! results concat rows*)
            (reset! mxpages max-pages)
            (reset! cur-page page))
          (.error js/console (str "Error: " body))))))

(defn- loading []
  [:div "Loading"])
(def loading-comp (r/reactify-component loading))

(defn show-texts-modal [{:keys [show-texts?]} m]
  ;; Loading of external data with infinite scroll
  ;; mimics example at http://jsfiddle.net/joellanciaux/m9hyhwra/2/
  (let [hide                      #(reset! show-texts? false)
        results                   (r/atom [])
        current-page              (r/atom 0)
        loading?                  (r/atom false)
        max-pages                 (r/atom 0)
        external-results-per-page (r/atom 10)
        external-sort-column      (r/atom nil)
        external-sort-ascending   (r/atom true)
        get-data                  (partial get-external-data m
                                           results loading? max-pages current-page)]
    (r/create-class
      {:display-name
       "show-texts-modal"

       :component-did-mount
       #(get-data 1)

       :reagent-render
       (fn [a {:keys [metadata-categories]}]
         (let [fetched-pages (atom #{})]
           [b/modal {:bs-size "large"
                     :show    true
                     :on-hide hide}
            [b/modalheader {:close-button true}
             [b/modaltitle "Corpus texts"]
             [text-selection a m]]
            [b/modalbody
             [:> js/Griddle
              {:use-griddle-styles         false
               :columns                    (map category-name @metadata-categories)
               :column-metadata            (for [cat @metadata-categories]
                                             {:columnName   (category-name cat)
                                              :cssClassName (str "column-" (:code cat))})
               :use-external               true
               :external-set-page          (fn [page]
                                             (when-not (contains? @fetched-pages page)
                                               (swap! fetched-pages conj page)
                                               (get-data page)))
               :enable-sort                false
               :external-set-page-size     #()
               :external-max-page          (inc @max-pages)
               :external-change-sort       #()
               :external-set-filter        #()
               :external-current-page      @current-page
               :results                    @results
               :table-class-name           "table"
               :results-per-page           @external-results-per-page
               :external-sort-column       @external-sort-column
               :external-sort-ascending    @external-sort-ascending
               :external-loading-component loading-comp
               :external-is-loading        @loading?
               :enable-infinite-scroll     true
               :body-height                (- (.. (ViewportSizeMonitor.) getSize -height) 300)
               :use-fixed-header           true}]]
            [b/modalfooter
             [b/button {:on-click hide} "Close"]]]))})))