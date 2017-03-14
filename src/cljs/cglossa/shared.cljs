(ns cglossa.shared
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as str]
            [cljs.core.async :as async :refer [<! promise-chan]]
            [cljs-http.client :as http]
            react-spinner
            [cglossa.react-adapters.bootstrap :as b]
            [reagent.core :as r :include-macros true]))

;; TODO: Make this configurable?
(def page-size 50)

;; If the search returns fewer results than we asked for, we assume that no more can be found.
;; However, since CQP may actually return fewer results than we asked for (even though more can
;; actually be found), we subtract this margin to be on the safe side.
(def result-margin 200)

(def geo-map-colors [:yellow :green :blue :purple :black :white :red :orange])

;; Adapt https://github.com/chenglou/react-spinner to Reagent
(def spinner (r/adapt-react-class js/Spinner))

(defn spinner-overlay
  "Container component that covers its child components with a semi-transparent overlay
  that also shows a spinner.

  Supported options are :spin?, which shows the overlay and spinner when true, and CSS
  styles that will override the default styles for the spinner.

  Note: Although it would seem more natural to have :spin? and the various
  styles as separate arguments, it seems that Reagent only supports a single
  argument when child components are provided as well; hence we need to put
  everything into the 'options' argument."
  [options]
  ;; Wrap everything in a relatively positioned div (whose width and height will be
  ;; determined by the child components of c) and insert an absolutely positioned div
  ;; with a high z-index that will fill the same space, thus overlaying the child
  ;; components.
  [:div {:style {:position "relative"}}
   (when (:spin? options)
     [:div {:style {:position         "absolute"
                    :top              0
                    :right            0
                    :bottom           0
                    :left             0
                    :background-color "white"
                    :opacity          0.7
                    :z-index          1000}}
      [spinner {:style (dissoc options :spin?)}]])
   (map-indexed (fn [i child]
                  (r/as-element (with-meta child {:key i})))
                (r/children (r/current-component)))])

(defn reset-queries! [{{:keys [queries]} :search-view} {:keys [corpus]}]
  (let [language-code (-> @corpus :languages first :code)]
    (reset! queries [{:query "[]" :lang language-code :exclude? false}])))

(def ^:private cancel-search-ch
  "Core.async channel used to cancel any already ongoing search when we start a new one."
  (async/chan))

(defmulti extra-navbar-items (fn [corpus] (:code @corpus)))

(defmethod extra-navbar-items :default [_] nil)

(defmulti cleanup-result
  "Multimethod that accepts two arguments - a model/domain state map and a
  single search result - and dispatches to the correct method based on
  the value of :search-engine in the corpus map found in the
  model/domain state map. The :default case implements CWB support."
  (fn [{corpus :corpus} _] (:search-engine @corpus)))

(defn- do-search-steps! [{:keys                              [searching?]
                          {:keys [queries]}                  :search-view
                          {:keys [results total cpu-counts]} :results-view}
                         {:keys [search] :as m}
                         url search-params nsteps]
  (go
    (dotimes [step nsteps]
      (let [json-params (cond-> search-params
                                true (assoc :step (inc step))
                                @total (assoc :last-count @total)
                                ;; If the currently specified queries differ from the ones on
                                ;; the search that was last received from the server, leave the
                                ;; ID blank in order to generate a new search; otherwise regard
                                ;; this request as a refinement of the latest search and hence
                                ;; keep its ID.
                                (= (:queries @search) (str @queries)) (assoc :search-id (:id @search)))
            ;; Fire off a search query
            results-ch  (http/post url {:json-params json-params})
            ;; Wait for either the results of the query or a message to cancel the query
            ;; because we have started another search
            [val ch] (async/alts! [cancel-search-ch results-ch] :priority true)]
        (when (= ch results-ch)
          (let [{:keys [status success] {resp-search     :search
                                         resp-results    :results
                                         resp-count      :count
                                         resp-cpu-counts :cpu-counts} :body} val]
            (when (= status 401)
              (reset! (:authenticated-user m) nil))
            (if-not success
              (.log js/console status)
              (do
                (swap! search merge resp-search)
                ;; Add the number of hits found by each cpu core in this search step
                (swap! cpu-counts concat resp-cpu-counts)
                ;; Only the first request actually returns results; the others just save the
                ;; results on the server to be fetched on demand and return an empty result list
                ;; (but a non-zero resp-count), unless the first result did not find enough
                ;; results to fill up two result pages - in that case, later requests will
                ;; continue filling them. Thus, we set the results if we either receive a
                ;; non-empty list of results or a resp-count of zero (meaning that there were
                ;; actually no matches).
                (if (or (seq resp-results) (zero? resp-count))
                  (let [old-results (apply concat (map second @results))]
                    (reset! results (into {} (map (fn [page-index res]
                                                    [(inc page-index)
                                                     (map (partial cleanup-result m) res)])
                                                  (range)
                                                  (partition-all page-size
                                                                 (concat old-results
                                                                         resp-results)))))
                    (reset! total (or resp-count (count resp-results))))
                  (reset! total resp-count))))))))
    (reset! searching? false)))

(defn selected-metadata-ids [search]
  (->> (:metadata @search) (filter #(second %)) (into {})))

(defn reset-results!
  [{{:keys [queries]}                   :search-view
    {:keys                    [results cpu-counts page-no paginator-page-no
                               paginator-text-val fetching-pages translations]
     {:keys [geo-data colored-phons
             selected-color]} :geo-map} :results-view}
   {:keys [search]}]
  (reset! results nil)
  (reset! cpu-counts [])
  (reset! page-no 1)
  (reset! paginator-page-no 1)
  (reset! paginator-text-val 1)
  (reset! fetching-pages #{})
  (reset! translations {})
  (reset! geo-data {})
  ;; Don't remove selected map colours if we are just filtering an existing search
  (when (not= (:queries @search) (str @queries))
    (reset! colored-phons (zipmap geo-map-colors (repeat #{})))
    (reset! selected-color :yellow)))

(defn queries->param [corpus queries]
  (let [q (if (= (-> corpus :languages first :code) "zh")
            ;; For Chinese: If the tone number is missing, add a pattern
            ;; that matches all tones
            (for [query queries]
              (update query :query
                      str/replace #"\bphon=\"([^0-9\"]+)\"" "phon=\"$1[1-4]?\""))
            ;; For other languages, leave the queries unmodified
            queries)]
    (for [qu q]
      (update qu :query str/replace "\"__QUOTE__\"" "'\"'"))))

(defn all-displayed-attrs [corpus]
  (cons [:word "Word form"] (->> @corpus :languages first :config :displayed-attrs)))

(defn stats!
  ([a {:keys [corpus] :as m}]
    ;; Do three search steps only if multicpu_bounds is defined for this corpus
   (stats! a m (if (:multicpu-bounds @corpus) 3 1)))
  ([{{queries :queries}                                        :search-view
     {:keys [total context-size
             sort-key freq-attr
             freq-attr-sorted freq-res
             freq-case-sensitive] {:keys [geo-data]} :geo-map} :results-view
     searching?                                                :searching?
     show-results?                                             :show-results?
     :as                                                       a}
    {:keys [corpus search] :as m}
    nsteps]
   (let [first-query          (:query (first @queries))
         freq-attr-sorted-val (filter #((first %) @freq-attr) (all-displayed-attrs corpus))]
     (reset! freq-attr-sorted freq-attr-sorted-val)
     (reset! freq-res "Please wait... (frequency counting may take some time)")
     (when (and first-query
                (not (str/blank? first-query))
                (not= first-query "\"\""))
       ;; Start by cancelling any already ongoing search.
       (async/offer! cancel-search-ch true)
       (let [q            (queries->param @corpus @queries)
             url          (str (:code @corpus) "/stats")
             sel-metadata (selected-metadata-ids search)
             params       {:queries      q
                           :metadata-ids sel-metadata
                           :page-size    page-size
                           :context-size @context-size
                           :sort-key     @sort-key
                           :freq-attr    (map first freq-attr-sorted-val)
                           :freq-case-sensitive @freq-case-sensitive}]
         (go
           ;; Wait for the search to finish before fetching geo-map data
           (<! (let [json-params (cond-> params
                                         @total (assoc :last-count @total)
                                         ;; If the currently specified queries differ from the ones on
                                         ;; the search that was last received from the server, leave the
                                         ;; ID blank in order to generate a new search; otherwise regard
                                         ;; this request as a refinement of the latest search and hence
                                         ;; keep its ID.
                                         (= (:queries @search) (str @queries)) (assoc :search-id (:id @search)))
                     ;; Fire off a search query
                     results-ch  (http/post url {:json-params json-params})
                     ;; Wait for either the results of the query or a message to cancel the query
                     ;; because we have started another search
                     [val ch] (async/alts! [cancel-search-ch results-ch] :priority true)]
                 (when (= ch results-ch)
                   (let [{:keys [status success] {resp-search     :search
                                                  resp-results    :results
                                                  resp-count      :count
                                                  resp-cpu-counts :cpu-counts} :body} val]
                     (when (= status 401)
                       (reset! (:authenticated-user m) nil))
                     (reset! freq-res resp-results)
                     (promise-chan nil)))))))))))

(defn search!
  ([a {:keys [corpus] :as m}]
    ;; Do three search steps only if multicpu_bounds is defined for this corpus
   (search! a m (if (:multicpu-bounds @corpus) 3 1)))
  ([{{:keys [queries num-random-hits random-hits-seed]}                :search-view
     {:keys [total context-size sort-key] {:keys [geo-data]} :geo-map} :results-view
     searching?                                                        :searching?
     show-results?                                                     :show-results?
     :as                                                               a}
    {:keys [corpus search] :as m}
    nsteps]
   (let [first-query (-> @queries first :query str/trim)]
     (when (and first-query
                (not (str/blank? first-query))
                (not= first-query "\"\"")
                ;; Check for one or more empty terms possibly separated by intervals
                (not (re-matches #"\[\](\s*\[\](\{\d*,\d*\})?)*" first-query)))
       ;; Start by cancelling any already ongoing search.
       (async/offer! cancel-search-ch true)
       (let [q            (queries->param @corpus @queries)
             url          (str (:code @corpus) "/search")
             sel-metadata (selected-metadata-ids search)
             params       {:queries          q
                           :metadata-ids     sel-metadata
                           :page-size        page-size
                           :context-size     @context-size
                           :sort-key         @sort-key
                           :num-random-hits  @num-random-hits
                           :random-hits-seed @random-hits-seed}]
         (reset! show-results? true)
         (reset! searching? true)
         (reset! total nil)
         (reset! sort-key :position)
         (reset-results! a m)
         (go
           ;; Wait for the search to finish before fetching geo-map data
           (<! (do-search-steps! a m url params nsteps))
           (when (:geo-coords @corpus)
             (let [geo-results-ch (http/post (str (:code @corpus) "/geo-distr")
                                             {:json-params {:search-id    (:id @search)
                                                            :metadata-ids sel-metadata}})
                   {{geo-results :results} :body} (<! geo-results-ch)]
               (reset! geo-data geo-results)))))))))

(defn showing-metadata? [{:keys [show-results? show-metadata? narrow-view?]}
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

(defn on-key-down [event a m & params]
  (when (= "Enter" (.-key event))
    (.preventDefault event)
    (apply search! a m params)))

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

(defn segment-initial-checkbox [wrapped-query speech?]
  [:label.checkbox-inline {:style {:padding-left 18}}
   [:input {:type      "checkbox"
            :style     {:margin-left -18}
            :checked   (:segment-initial? @wrapped-query)
            :on-change (fn [e]
                         (let [query     (:query @wrapped-query)
                               checked?  (.-target.checked e)
                               s-tag     (if speech? "<sync>" "<s>")
                               new-query (if checked?
                                           (str s-tag query)
                                           (str/replace query (re-pattern (str "^" s-tag)) ""))]
                           (swap! wrapped-query
                                  assoc :query new-query :segment-initial? checked?)))}]
   (str " " (if speech? "Segment" "Sentence") " initial")])

(defn segment-final-checkbox [wrapped-query speech?]
  [:label.checkbox-inline {:style {:padding-left 18}}
   [:input {:type      "checkbox"
            :style     {:margin-left -18}
            :checked   (:segment-final? @wrapped-query)
            :on-change (fn [e]
                         (let [query     (:query @wrapped-query)
                               checked?  (.-target.checked e)
                               s-tag     (if speech? "</sync>" "</s>")
                               new-query (if checked?
                                           (str query s-tag)
                                           (str/replace query (re-pattern (str s-tag "$")) ""))]
                           (swap! wrapped-query
                                  assoc :query new-query :segment-final? checked?)))}]
   (str " " (if speech? "Segment" "Sentence") " final")])

(defn headword-search-checkbox [wrapped-query]
  [b/checkbox
   {:value     "1"
    :checked   (:headword-search @wrapped-query)
    :on-change #(swap! wrapped-query assoc :headword-search (.-target.checked %))
    :id        "headword_search"
    :name      "headword_search"} " Headword search"])

(defn top-toolbar [{:keys                       [num-resets show-results? show-metadata?
                                                 num-selected-texts num-selected-tokens]
                    {:keys [view-type queries]} :search-view
                    {:keys [sort-key]}          :results-view
                    :as                         a}
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
                           (reset-queries! a m)
                           (reset! view-type :simple)
                           (reset! search {})
                           (reset! show-results? false)
                           (reset! sort-key :position)
                           (reset! num-selected-texts nil)
                           (reset! num-selected-tokens nil)
                           (swap! num-resets inc))} ; see comments in the start component
     "Reset form"]]])
