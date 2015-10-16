(ns cglossa.results
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cglossa.search-views.shared :refer [search-inputs]]
            [cglossa.shared :refer [top-toolbar]]
            [cglossa.search-views.cwb.shared :refer [page-size search!]]
            [cglossa.react-adapters.bootstrap :as b]))

(defn- results-info [{{total :total} :results-view searching? :searching?}]
  [:div.col-sm-5
   (if (pos? @total)
     (let [npages    (-> (/ @total page-size) Math/ceil int)
           pages-str (if (= npages 1) " page" " pages")]
       (if @searching?
         (str "Showing the first " @total " matches (" npages pages-str "); searching for more...")
         (str "Found " @total " matches (" npages " pages)")))
     (when @searching? "Searching..."))])

(defn- sort-button [{{sb :sort-by total :total} :results-view searching? :searching? :as a} m]
  (let [sort-by   @sb
        on-select (fn [event-key _ _]
                    (reset! sb (keyword event-key))
                    (search! a m))]
    [b/dropdownbutton {:title    "Sort"
                       :bs-size  "small"
                       :disabled (or @searching?
                                     (zero? @total))
                       :style    {:margin-bottom 10}}
     [b/menuitem {:event-key :position, :on-select on-select}
      (when (= sort-by :position) [b/glyphicon {:glyph "ok"}]) "  By corpus position"]
     [b/menuitem {:event-key :match, :on-select on-select}
      (when (= sort-by :match) [b/glyphicon {:glyph "ok"}]) "  By match"]
     [b/menuitem {:event-key :left, :on-select on-select}
      (when (= sort-by :left) [b/glyphicon {:glyph "ok"}]) "  By left context"]
     [b/menuitem {:event-key :right, :on-select on-select}
      (when (= sort-by :right) [b/glyphicon {:glyph "ok"}]) "  By right context"]]))

(defn- statistics-button [{{freq-attr :freq-attr} :results-view} m]
  (let [on-select #(reset! freq-attr (keyword %1))]
    [b/dropdownbutton {:title "Statistics"}
     [b/menuitem {:header true} "Frequencies"]
     [b/menuitem {:event-key :word, :on-select on-select} "Word forms"]
     [b/menuitem {:event-key :lemma, :on-select on-select} "Lemmas"]
     [b/menuitem {:event-key :pos, :on-select on-select} "Parts-of-speech"]]))

;; Set of result pages currently being fetched. This is temporary application state that we
;; don't want as part of the top-level app-state ratom
(def fetching-pages (atom #{}))

(defn- fetch-results! [{{:keys [results page-no paginator-page-no sort-by]} :results-view}
                       search-id new-page-no]
  ;; Don't fetch the page if we already have a request for the same page in flight
  (when-not (get @fetching-pages new-page-no)
    ;; Register the new page as being fetched
    (swap! fetching-pages conj new-page-no)
    (go
      (let [start      (* (dec new-page-no) page-size)
            end        (+ start (dec page-size))
            results-ch (http/get "/results" {:query-params {:search-id search-id
                                                            :start     start
                                                            :end       end
                                                            :sort-by   sort-by}})
            {:keys [status success] page-results :body} (<! results-ch)]
        ;; Remove the new page from the set of pages currently being fetched
        (swap! fetching-pages disj new-page-no)
        (if-not success
          (.log js/console status)
          (do
            (swap! results assoc new-page-no page-results)
            ;; Don't show the fetched page if we have already selected another page in the
            ;; paginator while we were waiting for the request to finish
            (when (= new-page-no @paginator-page-no)
              (reset! page-no new-page-no))))))))

(defn- pagination [{{:keys [results total page-no paginator-page-no]} :results-view :as a}
                   {:keys [search]}]
  (let [last-page-no #(inc (quot @total page-size))
        set-page     (fn [e n]
                       (.preventDefault e)
                       (let [new-page-no (js/parseInt n)]
                         (when (<= 1 new-page-no (last-page-no))
                           ;; Set the value of the page number shown in the paginator; it may
                           ;; differ from the page shown in the result table until we have
                           ;; actually fetched the data from the server
                           (reset! paginator-page-no new-page-no)
                           (if (get @results new-page-no)
                             ;; The selected result page has already been fetched from the
                             ;; server and can be shown in the result table immediately
                             (reset! page-no new-page-no)
                             ;; Otherwise, we need to fetch the results from the server
                             ;; before setting page-no in the top-level app-data structure
                             (fetch-results! a (:rid @search) new-page-no)))))]
    (when (> @total page-size)
      [:div.pull-right
       [:nav
        [:ul.pagination.pagination-sm
         [:li {:class-name (when (= @paginator-page-no 1)
                             "disabled")}
          [:a {:href       "#"
               :aria-label "First"
               :title      "First"
               :on-click   #(set-page % 1)}
           [:span {:aria-hidden "true"} "«"]]]
         [:li {:class-name (when (= @paginator-page-no 1)
                             "disabled")}
          [:a {:href       "#"
               :aria-label "Previous"
               :title      "Previous"
               :on-click   #(set-page % (dec @paginator-page-no))}
           [:span {:aria-hidden "true"} "‹"]]]
         [:li
          [:input.form-control.input-sm {:style     {:text-align    "right"
                                                     :width         60
                                                     :float         "left"
                                                     :height        29
                                                     :border-radius 0}
                                         :value     @paginator-page-no
                                         :on-click  #(.select (.-target %))
                                         :on-change #(set-page % (.-target.value %))}]]
         [:li {:class-name (when (= @paginator-page-no (last-page-no))
                             "disabled")}
          [:a {:href       "#"
               :aria-label "Next"
               :title      "Next"
               :on-click   #(set-page % (inc @paginator-page-no))}
           [:span {:aria-hidden "true"} "›"]]]
         [:li {:class-name (when (= @paginator-page-no (last-page-no))
                             "disabled")}
          [:a {:href       "#"
               :aria-label "Last"
               :title      "Last"
               :on-click   #(set-page % (last-page-no))}
           [:span {:aria-hidden "true"} "»"]]]]]])))

(defn- concordance-toolbar [a m]
  [:div.row {:style {:margin-top 15}}
   [:div.col-sm-12
    [b/buttontoolbar
     [sort-button a m]
     [pagination a m]]]])

(defmulti concordance-table
  "Multimethod that accepts two arguments - an app state map and a
  model/domain state map - and dispatches to the correct method based
  on the value of :search-engine in the corpus map found in the
  model/domain state map. The :default case implements CWB support."
  (fn [_ {corpus :corpus}] (:search-engine @corpus)))

(defn- concordances [a m]
  [:div.container-fluid {:style {:padding-left 0 :padding-right 0}}
   [concordance-toolbar a m]
   [concordance-table a m]
   [:div.row
    [:div.col-sm-12
     [pagination a]]]])

(defn results [{:keys [num-resets] :as a} m]
  [:div
   [:div.row
    [top-toolbar a]
    [results-info a]]
   ^{:key @num-resets} [search-inputs a m]                  ; See comments in cglossa.start
   [b/tabbedarea {:style              {:margin-top 15}
                  :animation          false
                  :default-active-key :concordance}
    [b/tabpane {:tab "Concordance" :event-key :concordance}
     [concordances a m]]
    [b/tabpane {:tab "Statistics" :event-key :statistics}]]])
