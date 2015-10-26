(ns cglossa.shared
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]))

;; TODO: Make this configurable?
(def page-size 50)

;; If the search returns fewer results than we asked for, we assume that no more can be found.
;; However, since CQP may actually return fewer results than we asked for (even though more can
;; actually be found), we subtract this margin to be on the safe side.
(def result-margin 200)

(defmulti cleanup-result
  "Multimethod that accepts two arguments - a model/domain state map and a
  single search result - and dispatches to the correct method based on the
  value of :search-engine in the corpus map found in the model/domain state
  map. The :default case implements CWB support."
  (fn [{corpus :corpus} _] (:search-engine @corpus)))

(defn- search-step3 [url params total searching? search-id]
  "Performs an unrestricted search."
  (go
    (let [results-ch (http/post url {:json-params (merge params {:step      3
                                                                 :cut       nil
                                                                 :search-id search-id})})
          {:keys [status success] {res :result} :body} (<! results-ch)]
      (if-not success
        (.log js/console status)
        (do
          ;; The results from the third request should be the number of results found so far.
          ;; Just set the total ratom (we'll postpone fetching any results until the user switches
          ;; to a different result page) and mark searching as finished.
          (reset! total res)
          (reset! searching? false))))))

(defn- search-step2 [url params total searching? search-id]
  "Performs a search restricted to 20 pages of search results."
  (go
    (let [results-ch (http/post url {:json-params (merge params {:step      2
                                                                 :cut       (* 20 page-size)
                                                                 :search-id search-id})})
          {:keys [status success] {res :result} :body} (<! results-ch)]
      (if-not success
        (.log js/console status)
        (do
          ;; The response from the second request should be the number of results found so far.
          ;; Just set the total ratom - we'll postpone fetching any results until the user switches
          ;; to a different result page.
          (reset! total res)
          (if (< res (- (* 20 page-size) result-margin))
            ;; We found less than 20 search pages (minus the safety margin) of results,
            ;; so stop searching
            (reset! searching? false)
            (search-step3 url params total searching? search-id)))))))

(defn- search-step1 [m url params total searching? current-search current-results]
  "Performs a search restricted to one page of search results."
  (go
    (let [results-ch (http/post url {:json-params (merge params {:step 1 :cut (* 2 page-size)})})
          {:keys [status success] {:keys [search result]} :body} (<! results-ch)]
      (if-not success
        (.log js/console status)
        (do
          (swap! current-search merge search)
          ;; The response from the first request should be (at most) two pages of search results.
          ;; Set the results ratom to those results and the total ratom to the number of results.
          (reset! current-results (into {} (map (fn [page-index res]
                                                  [(inc page-index)
                                                   (map (partial cleanup-result m) res)])
                                                (range)
                                                (partition-all page-size result))))
          (reset! total (count result))
          ;; Since CQP may return fewer than the number or results we asked for, always do at
          ;; least one more search
          (search-step2 url params total searching? (:rid search)))))))

(defn search! [{{queries :queries}                   :search-view
                {:keys [show-results? results total page-no
                        paginator-page-no
                        paginator-text-val sort-by]} :results-view
                searching?                           :searching?}
               {:keys [corpus search] :as m}]
  (let [first-query (:query (first @queries))]
    (when (and first-query
               (not= first-query "\"\""))
      (let [q      (if (= (:lang @corpus) "zh")
                     ;; For Chinese: If the tone number is missing, add a pattern
                     ;; that matches all tones
                     (for [query @queries]
                       (update query :query
                               str/replace #"\bphon=\"([^0-9\"]+)\"" "phon=\"$1[1-4]?\""))
                     ;; For other languages, leave the queries unmodified
                     @queries)
            url    "/search"
            params {:corpus-id    (:rid @corpus)
                    :queries      q
                    :metadata-ids (:metadata @search)
                    :sort-by      @sort-by}]
        (reset! show-results? true)
        (reset! results nil)
        (reset! searching? true)
        (reset! total 0)
        (reset! page-no 1)
        (reset! paginator-page-no 1)
        (reset! paginator-text-val 1)
        (search-step1 m url params total searching? search results)))))

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

(defn top-toolbar [{:keys [num-resets show-metadata?] {:keys [queries]} :search-view :as a} m]
  [:div.col-sm-5
   [b/buttontoolbar {:style {:margin-bottom 20}}
    (if (showing-metadata? a m)
      [b/button {:bs-size  "xsmall"
                 :title    "Hide search criteria"
                 :on-click (fn [e]
                             (reset! show-metadata? false)
                             (.preventDefault e))}
       "Hide filters"]
      [b/button {:bs-size  "xsmall"
                 :title    "Show search criteria"
                 :on-click (fn [e]
                             (reset! show-metadata? true)
                             (.preventDefault e))}
       "Filters"])
    [b/button {:bs-style "primary"
               :bs-size  "xsmall"
               :title    "Reset form"
               :on-click (fn []
                           (reset! queries [{:query ""}])
                           (swap! num-resets inc))}         ; see comments in the start component
     "Reset form"]]])
