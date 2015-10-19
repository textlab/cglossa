(ns cglossa.results
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
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
(def ^:private fetching-pages (atom #{}))
(def ^:private result-window-halfsize 1)

(defn- fetch-result-window!
  "Fetches a window of search result pages centred on centre-page-no. Ignores pages that have
  already been fetched or that are currently being fetched in another request (note that such
  pages can only be located at the edges of the window, and not as 'holes' within the window,
  since they must have been fetched as part of an earlier window)."
  [{{:keys [results sort-by]} :results-view} search-id centre-page-no last-page-no]
  ;; Enclose the whole procedure in a go block. This way, the function will return the channel
  ;; returned by the go block, which will receive
  (go
    (let [;; Make sure the edges of the window are between 1 and last-page-no
          start-page (max (- centre-page-no result-window-halfsize) 1)
          end-page   (min (+ centre-page-no result-window-halfsize) last-page-no)
          _          (.log js/console start-page)
          _          (.log js/console end-page)
          page-nos   (as-> (range start-page (inc end-page)) $
                           ;; Ignore pages currently being fetched by another request
                           (remove #(contains? @fetching-pages %) $)
                           ;; Ignore pages that have already been fetched
                           (remove #(contains? @results %) $)
                           ;; Create a new sequence to make sure we didn't create any "holes" in
                           ;; it (although that should not really happen in practice since we
                           ;; always fetch whole windows of pages)
                           (if (empty? $)
                             $
                             (range (first $) (inc (last $)))))]
      (if (empty? page-nos)
        ;; All pages are either being fetched or already fetched, so just return a keyword on the
        ;; channel returned by the go block and hence the function
        ::no-pages-to-fetch

        (let [;; Calculate the first and last result index (zero-based) to request from the server
              first-result (* page-size (dec (first page-nos)))
              last-result  (dec (* page-size (last page-nos)))
              _            (.log js/console page-nos)
              _            (.log js/console first-result)
              _            (.log js/console last-result)
              results-ch   (http/get "/results" {:query-params {:search-id search-id
                                                                :start     first-result
                                                                :end       last-result
                                                                :sort-by   @sort-by}})
              ;; Parks until results are available on the core.async channel
              {:keys [status success] req-result :body} (<! results-ch)]
          ;; Register the pages as being fetched
          (swap! fetching-pages #(apply conj % page-nos))
          ;; Remove the pages from the set of pages currently being fetched
          (swap! fetching-pages #(apply disj % page-nos))
          (if-not success
            (.log js/console status)
            ;; Add the fetched pages to the top-level results ratom
            (swap! results merge (zipmap page-nos (partition-all page-size req-result))))
          ;; This keyword will be put on the channel returned by the go block
          ;; and hence the function
          ::fetched-pages)))))

(defn- pagination [{{:keys [results total page-no
                            paginator-page-no paginator-text-val]} :results-view :as a}
                   {:keys [search]}]
  (let [last-page-no #(inc (quot (dec @total) page-size))
        set-page     (fn [e n]
                       (.preventDefault e)
                       (let [new-page-no (js/parseInt n)
                             last        (last-page-no)]
                         (when (<= 1 new-page-no last)
                           ;; Set the value of the page number shown in the paginator; it may
                           ;; differ from the page shown in the result table until we have
                           ;; actually fetched the data from the server
                           (reset! paginator-page-no new-page-no)
                           (reset! paginator-text-val new-page-no)
                           (if (contains? @results new-page-no)
                             (do
                               ;; If the selected result page has already been fetched from the
                               ;; server, it can be shown in the result table immediately
                               (reset! page-no new-page-no)
                               ;; If necessary, fetch any result pages in a window centred around
                               ;; the selected page in order to speed up pagination to nearby
                               ;; pages. No need to wait for it to finish though.
                               (fetch-result-window! a (:rid @search) new-page-no last))
                             (go
                               ;; Otherwise, we need to park until the results from the server
                               ;; arrive before setting the page to be shown in the result table
                               (<! (fetch-result-window! a (:rid @search) new-page-no last))
                               ;; Don't show the fetched page if we have already selected another
                               ;; page in the paginator while we were waiting for the request
                               ;; to finish
                               (when (= new-page-no @paginator-page-no)
                                 (reset! page-no new-page-no)))))))
        on-key-down  (fn [e]
                       (when (= "Enter" (.-key e))
                         (if (str/blank? @paginator-text-val)
                           ;; If the text field is blank when we hit Enter, set its value
                           ;; to the last selected page number instead
                           (reset! paginator-text-val @paginator-page-no)
                           ;; Otherwise, make sure the entered number is within valid
                           ;; bounds and set it to be the new selected page
                           (let [last    (last-page-no)
                                 new-val (as-> @paginator-text-val v
                                               (js/parseInt v)
                                               (if (pos? v) v 1)
                                               (if (<= v last) v last))]
                             (set-page e new-val)))))]
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
          [:input.form-control.input-sm
           {:style       {:text-align    "right"
                          :width         60
                          :float         "left"
                          :height        29
                          :border-radius 0}
            :value       @paginator-text-val
            :on-click    #(.select (.-target %))
            :on-change   (fn [e]
                           (let [v (.-target.value e)]
                             ;; Allow the text field to contain a number or be blank while
                             ;; we are editing it
                             (when (or (integer? (js/parseInt v)) (str/blank? v))
                               (reset! paginator-text-val v))))
            :on-key-down on-key-down}]]
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
    [top-toolbar a m]
    [results-info a]]
   ^{:key @num-resets} [search-inputs a m]                  ; See comments in cglossa.start
   [b/tabbedarea {:style              {:margin-top 15}
                  :animation          false
                  :default-active-key :concordance}
    [b/tabpane {:tab "Concordance" :event-key :concordance}
     [concordances a m]]
    [b/tabpane {:tab "Statistics" :event-key :statistics}]]])
