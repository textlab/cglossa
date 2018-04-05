(ns cglossa.show-texts
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [cljs-http.client :as http]
            griddle
            [cglossa.result-views.cwb.shared :refer [line-showing-metadata]]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.metadata-list :refer [text-selection]]
            [cglossa.results :refer [show-texts-extra-col-name show-texts-extra-col-comp]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import [goog.dom ViewportSizeMonitor]))

(defn- category-name [{:keys [code name]}]
  (or name (-> code (str/replace "_" " ") str/capitalize)))

(defn- get-external-data [{:keys [corpus metadata-categories search] :as m}
                          sort-column sort-ascending? results loading? mxpages
                          cur-page fetched-tids page]
  (reset! loading? true)
  (go (let [sort-column-id (and @sort-column (->> @metadata-categories
                                                  (filter #(= (:name %) @sort-column))
                                                  first
                                                  :id))
            response       (<! (http/post (str (:code @corpus) "/texts")
                                          {:json-params
                                           {:selected-metadata (:metadata @search)
                                            :page              page
                                            :sort-column-id    sort-column-id
                                            :sort-ascending?   @sort-ascending?}}))
            {:keys [rows max-pages] :as body} (:body response)]
        (if (http/unexceptional-status? (:status response))
          (let [extra-col-name (:name (show-texts-extra-col-name corpus))
                rows*          (for [row rows
                                     :when (not (contains? @fetched-tids (get row 1)))]
                                 (let [standard-cols       (map (fn [cat]
                                                                  [(category-name cat)
                                                                   (get row (:id cat))])
                                                                @metadata-categories)
                                       corpus-specific-col {extra-col-name
                                                            (show-texts-extra-col-comp corpus m row)}]
                                   (.log js/console @fetched-tids)
                                   (swap! fetched-tids conj (get row 1))
                                   (into {"__dummy" " "} (cons corpus-specific-col standard-cols))))]
            (when (= (:status response) 401)
              (reset! (:authenticated-user m) nil))
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
        fetched-tids              (atom #{})
        external-results-per-page (r/atom 10)
        external-sort-column      (r/atom nil)
        external-sort-ascending   (r/atom true)
        get-data                  (partial get-external-data m
                                           external-sort-column external-sort-ascending
                                           results loading? max-pages current-page
                                           fetched-tids)]
    (r/create-class
      {:display-name
       "show-texts-modal"

       :component-did-mount
       #(get-data 1)

       :reagent-render
       (fn [a {:keys [corpus metadata-categories]}]
         (r/with-let [fetched-pages (atom #{})
                      extra-column-name (show-texts-extra-col-name corpus)]
           [:div.show-texts-popup
            [b/modal {:show              true
                      :on-hide           hide
                      :dialog-class-name "show-texts-popup"}
             [b/modalheader {:close-button true}
              [b/modaltitle (if (= (:search-engine @corpus) "cwb_speech")
                              "Speakers"
                              "Corpus texts")]
              [text-selection a m]]
             [b/modalbody
              [:> js/Griddle
               {:use-griddle-styles         false
                :columns                    (->> @metadata-categories
                                                 (cons extra-column-name)
                                                 (filter #(:code %))
                                                 (remove #(str/starts-with? (name (:code %)) "hd_"))
                                                 (mapv category-name)
                                                 ;; Add dummy column to fill remaining space
                                                 (#(conj % "__dummy")))
                :column-metadata            (concat (for [cat @metadata-categories]
                                                      {:columnName   (category-name cat)
                                                       :cssClassName (str "column-" (:code cat))})
                                                    [{:columnName  "__dummy"
                                                      :displayName " "}
                                                     {:columnName      (:name extra-column-name)
                                                      :cssClassName    "extra-text-column"
                                                      :customComponent (r/as-element
                                                                         (show-texts-extra-col-comp
                                                                           corpus m))}])
                :use-external               true
                :external-set-page          (fn [page]
                                              (when-not (contains? @fetched-pages page)
                                                (swap! fetched-pages conj page)
                                                (get-data page)))
                :enable-sort                true
                :external-set-page-size     #()
                :external-max-page          (inc @max-pages)
                :external-change-sort       (fn [column, ascending?]
                                              (reset! external-sort-column column)
                                              (reset! external-sort-ascending ascending?)
                                              (reset! results [])
                                              (reset! fetched-pages #{})
                                              (reset! fetched-tids #{})
                                              (reset! current-page 0))
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
              [b/button {:on-click hide} "Close"]]]]))})))
