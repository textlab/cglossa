(ns cglossa.show-texts
  (:require [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cljs-http.client :as http]
            griddle)
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import [goog.dom ViewportSizeMonitor]))

(def table (r/adapt-react-class js/Griddle))

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
          (let [cat-names (map :name (sort-by :id @metadata-categories))
                rows*     (map zipmap (repeat cat-names) rows)]
            (reset! loading? false)
            (swap! results concat rows*)
            (reset! mxpages max-pages)
            (reset! cur-page page))
          (.error js/console (str "Error: " body))))))

(defn loading []
  [:div "Loading"])
(def loading-comp (r/reactify-component loading))

(defn show-texts-modal [{:keys [show-texts?]} {:keys [metadata-categories] :as m}]
  ;; Loading of external data with infinite scroll
  ;; mimics example at http://jsfiddle.net/joellanciaux/m9hyhwra/2/
  (let [hide                      (fn [e]
                                    (reset! show-texts? false)
                                    (.preventDefault e))
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
       (fn [_ {:keys [metadata-categories]}]
         (let [fetched-pages (atom #{})]
           [b/modal {:bs-size "large"
                     :show    true
                     :on-hide hide}
            [b/modalheader {:close-button true}
             [b/modaltitle "Corpus texts"]]
            [b/modalbody
             [table {:columns                    (map :name (sort-by :id @metadata-categories))
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
                     :body-width                 1000
                     :use-fixed-header           true}]]
            [b/modalfooter
             [b/button {:on-click hide} "Close"]]]))})))