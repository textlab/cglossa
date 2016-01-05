(ns cglossa.shared
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as str]
            [cljs.core.async :as async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]))

;; TODO: Make this configurable?
(def page-size 50)

;; If the search returns fewer results than we asked for, we assume that no more can be found.
;; However, since CQP may actually return fewer results than we asked for (even though more can
;; actually be found), we subtract this margin to be on the safe side.
(def result-margin 200)

(def ^:private cancel-search-ch
  "Core.async channel used to cancel any already ongoing search when we start a new one."
  (async/chan))

(defmulti cleanup-result
  "Multimethod that accepts two arguments - a model/domain state map and a
  single search result - and dispatches to the correct method based on the
  value of :search-engine in the corpus map found in the model/domain state
  map. The :default case implements CWB support."
  (fn [{corpus :corpus} _] (:search-engine @corpus)))

(defn- do-search-steps! [{:keys [searching?] {:keys [results total]} :results-view}
                         {:keys [search] :as m}
                         url search-params step-params]
  (go-loop [params step-params]
    (let [[step cut] (first params)
          json-params (cond-> search-params
                              true (assoc :step step :cut cut)
                              (:id @search) (assoc :search-id (:id @search)))
          ;; Fire off a search query
          results-ch  (http/post url {:json-params json-params})
          ;; Wait for either the results of the query or a message to cancel the query
          ;; because we have started another search
          [val ch] (async/alts! [cancel-search-ch results-ch] :priority true)]
      (when (= ch results-ch)
        (let [{:keys [status success] {resp-search  :search
                                       resp-results :results
                                       resp-count   :count} :body} val]
          (if-not success
            (.log js/console status)
            (do
              (swap! search merge resp-search)
              ;; Only the first request actually returns results; the others just save the results
              ;; on the server to be fetched on demand
              (if resp-results
                (do
                  (reset! results (into {} (map (fn [page-index res]
                                                  [(inc page-index)
                                                   (map (partial cleanup-result m) res)])
                                                (range)
                                                (partition-all page-size resp-results))))
                  (reset! total (count resp-results)))
                (reset! total resp-count))
              (if (or (nil? (next params))
                      (< resp-count (- cut result-margin)))
                ;; Either we haven't specified any more steps, or we found less results than we
                ;; asked for (minus the safety margin). In either case, don't do any more searches.
                (reset! searching? false)
                ;; Keep searching with the remaining step specifications
                (recur (next params))))))))))

(defn search! [{{queries :queries}                   :search-view
                {:keys [show-results? results total page-no
                        paginator-page-no
                        paginator-text-val sort-by]} :results-view
                searching?                           :searching?
                :as                                  a}
               {:keys [corpus search] :as m}]
  (let [first-query (:query (first @queries))]
    (when (and first-query
               (not (str/blank? first-query))
               (not= first-query "\"\""))
      ;; Start by cancelling any already ongoing search.
      (async/offer! cancel-search-ch true)
      (let [q      (if (= (:lang @corpus) "zh")
                     ;; For Chinese: If the tone number is missing, add a pattern
                     ;; that matches all tones
                     (for [query @queries]
                       (update query :query
                               str/replace #"\bphon=\"([^0-9\"]+)\"" "phon=\"$1[1-4]?\""))
                     ;; For other languages, leave the queries unmodified
                     @queries)
            url    "/search"
            params {:corpus-id    (:id @corpus)
                    :queries      q
                    :metadata-ids (->> (:metadata @search) (filter #(second %)) (into {}))
                    :sort-by      @sort-by}]
        (reset! show-results? true)
        (reset! results nil)
        (reset! searching? true)
        (reset! total nil)
        (reset! page-no 1)
        (reset! paginator-page-no 1)
        (reset! paginator-text-val 1)
        (do-search-steps! a m url params [[1 (* 2 page-size)] [2 (* 20 page-size)] [3 nil]])))))

(defn showing-metadata? [{:keys                   [show-metadata? narrow-view?]
                          {:keys [show-results?]} :results-view}
                         {:keys [metadata-categories]}]
  (cond
    ;; Don't show metadata if the corpus doesn't have any (duh!)
    (empty? @metadata-categories) false
    ;; If show-metadata is non-nil, the user has explicitly chosen whether to see metadata,
    ;; so we respect that unconditionally
    (some? @show-metadata?) @show-metadata?
    ;; Now we know that we have metadata, and that the user has not explicitly chosen
    ;; whether to see them. If we are showing search results, we hide the metadata if the
    ;; window is narrow; if instead we are showing the start page, we show the metadata
    ;; regardless of window size.
    @show-results? (not @narrow-view?)
    :else true))

(defn on-key-down [event a m]
  (when (= "Enter" (.-key event))
    (.preventDefault event)
    (search! a m)))

(defn remove-row-btn [show? wrapped-query]
  [:div.table-cell.remove-row-btn-container
   [b/button {:bs-style "danger"
              :bs-size  "xsmall"
              :title    "Remove row"
              :on-click #(reset! wrapped-query nil)
              :style    {:margin-right 5
                         :padding-top  3
                         :visibility   (if show?
                                         "visible"
                                         "hidden")}}
    [b/glyphicon {:glyph "remove"}]]])

(defn headword-search-checkbox [wrapped-query]
  [b/input {:type      "checkbox"
            :value     "1"
            :checked   (:headword-search @wrapped-query)
            :on-change #(swap! wrapped-query assoc :headword-search (.-target.checked %))
            :id        "headword_search"
            :name      "headword_search"} " Headword search"])

(defn top-toolbar [{:keys                   [num-resets show-metadata?]
                    {:keys [queries]}       :search-view
                    {:keys [show-results?]} :results-view
                    :as                     a}
                   {:keys [search metadata-categories] :as m}]
  [:div.col-sm-5
   [b/buttontoolbar {:style {:margin-bottom 20}}
    (when (seq @metadata-categories)
      (if (showing-metadata? a m)
        [b/button {:bs-size  "small"
                   :title    "Hide search criteria"
                   :on-click (fn [e]
                               (reset! show-metadata? false)
                               (.preventDefault e))}
         "Hide filters"]
        [b/button {:bs-size  "small"
                   :title    "Show search criteria"
                   :on-click (fn [e]
                               (reset! show-metadata? true)
                               (.preventDefault e))}
         "Filters"]))
    [b/button {:bs-style "primary"
               :bs-size  "small"
               :title    "Reset form"
               :on-click (fn []
                           (reset! queries [{:query ""}])
                           (reset! search {})
                           (reset! show-results? false)
                           (swap! num-resets inc))}         ; see comments in the start component
     "Reset form"]]])
