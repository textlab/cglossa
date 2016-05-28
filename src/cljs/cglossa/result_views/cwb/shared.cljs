(ns cglossa.result-views.cwb.shared
  (:require [clojure.string :as str]
            [reagent.core :as r]
            cljsjs.jquery
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private metadata-cache (atom {}))

(defn- metadata-overlay [result-showing-metadata]
  [b/overlay {:show      (not (nil? @result-showing-metadata))
              :placement "top"
              :target    (fn [] (:node @result-showing-metadata))}
   [:div {:style {:position         "absolute"
                  :background-color "#EEE"
                  :box-shadow       "0 5px 10px rgba (0, 0, 0, 0.2)"
                  :border           "1px solid #CCC"
                  :border-radius    3,
                  :margin-left      -5,
                  :margin-top       5,
                  :padding          15
                  :z-index          1000}}
    [:div {:style {:text-align "right" :margin-bottom 8}}
     [b/button {:bs-size  "xsmall"
                :style    {:margin-top -5 :padding-top 4}
                :on-click #(reset! result-showing-metadata nil)}
      [b/glyphicon {:glyph "remove"}]]]
    [:div.table-layout
     (map-indexed (fn [index [cat-name val]]
                    ^{:key index}
                    [:div.table-row
                     [:div.table-cell {:style {:padding-right 10}} cat-name]
                     [:div.table-cell val]])
                  (:vals @result-showing-metadata))]]])

(defn- find-result-node [result-hash]
  (.get (js/$ (str "#" result-hash)) 0))

(defn- get-result-metadata [e result-showing-metadata metadata-categories corpus-id
                            text-id result-hash]
  (.preventDefault e)
  (if-let [metadata (get @metadata-cache text-id)]
    ;; Use the metadata values from the cache, but set the DOM node to be the one
    ;; for the currently selected result (since the data in the cache may have been
    ;; for a different result with the same s-id).
    (reset! result-showing-metadata (assoc metadata :node (find-result-node result-hash)))
    (go
      (let [{:keys [body]} (<! (http/get "result-metadata" {:query-params {:corpus-id corpus-id
                                                                           :text-id   text-id}}))
            cat-names (into {} (map (fn [{:keys [id code name]}]
                                      (let [name* (or name (-> code
                                                               (str/replace "_" " ")
                                                               str/capitalize))]
                                        [id name*]))
                                    @metadata-categories))
            vals      (keep (fn [{:keys [metadata_category_id text_value]}]
                              (when-let [name (get cat-names metadata_category_id)]
                                [name text_value]))
                            body)
            metadata  {:node (find-result-node result-hash) :vals vals}]
        (swap! metadata-cache assoc text-id metadata)
        (reset! result-showing-metadata metadata)))))

(defn id-column [{{:keys [result-showing-metadata]} :results-view}
                 {:keys [corpus metadata-categories] :as m} result row-index]
  ;; If the 'match' property is defined, we know that we have a result from a monolingual
  ;; search or the first language of a multilingual one. If that is the case, and s-id is
  ;; defined, we print it in the first column (if we have a non-first language result, we
  ;; will include it in the next column instead).
  (let [s-id        (:s-id result)
        ;; Remove any "suffixes" from the s-id, since they typcially denote individual sentences,
        ;; which are irrelevant when fetching metadata.
        text-id     (str/replace s-id #"\..+" "")
        result-hash (hash result)]
    (when (and (:match result) s-id)
      [:td {:style {:vertical-align "middle"}}
       [:a {:href     ""
            :on-click #(get-result-metadata % result-showing-metadata metadata-categories
                                            (:id @corpus) text-id result-hash)}
        [:span {:id result-hash} s-id]]
       (metadata-overlay result-showing-metadata)
       [result-links a m result row-index]])))

(defn text-columns [result]
  (if (:match result)
    ;; If the 'match' value is defined, we know that we have a result from a monolingual
    ;; search or the first language of a multilingual one, and then we want pre-match, match
    ;; and post-match in separate columns.
    (list ^{:key 0} [:td.left-context (:pre-match result)]
          ^{:key 1} [:td.match (:match result)]
          ^{:key 2} [:td.right-context (:post-match result)])
    ;; Otherwise, we have a result from a non-first language of a multilingual search. In that
    ;; case, CQP doesn't mark the match, so we leave the first column blank and put all of the
    ;; text in a single following column.
    (list [:td]
          [:td.aligned-text {:col-span 3} (:pre-match result)])))
