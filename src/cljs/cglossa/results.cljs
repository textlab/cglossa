(ns cglossa.results
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cglossa.shared :refer [page-size geo-map-colors spinner-overlay search! all-displayed-attrs stats! top-toolbar
                                    selected-metadata-ids cleanup-result reset-results!
                                    queries->param]]
            [cglossa.search-views.shared :refer [search-inputs]]
            [cglossa.react-adapters.bootstrap :as b]
            geo-distribution-map))

(defn- results-info [{{:keys [total results]} :results-view searching? :searching?}]
  [:span
   [:div {:style {:position "absolute" :top 11 :right 0 :width 400 :text-align "right" :color "#555"}}
    (cond
      (pos? @total)
      (let [npages    (-> (/ @total page-size) Math/ceil int)
            pages-str (if (= npages 1) " page" " pages")]
        (if @searching?
          (str "Showing " @total " matches (" npages pages-str "); searching...")
          (str "Found " @total " matches (" npages " pages)")))

      (and (zero? @total) (not @searching?))
      "No matches found"

      :else
      (when @searching? "Searching..."))]
   [:div {:style {:position "absolute" :top 11 :right 0}}
    ;; Only show the spinner when we are searching AND have already found some results
    ;; so as to avoid showing spinners both here and over the result table at the same time
    ;; (since we show a spinner over the result table until we have found some results)
    [spinner-overlay {:spin? (and @searching? (seq @results)) :top 10}]]])

(defn- last-page-no [total]
  (-> (/ total page-size) Math/ceil int))

(def ^:private result-window-halfsize 1)

(defn- fetch-result-window!
  "Fetches a window of search result pages centred on centre-page-no. Ignores pages that have
  already been fetched or that are currently being fetched in another request (note that such
  pages can only be located at the edges of the window, and not as 'holes' within the window,
  since they must have been fetched as part of an earlier window)."
  [{{:keys [results total cpu-counts fetching-pages context-size sort-key]} :results-view}
   {:keys [corpus search] :as m}
   centre-page-no]
  ;; Enclose the whole procedure in a go block. This way, the function will return the channel
  ;; returned by the go block, which will receive the value of the body of the go block when
  ;; it is done parking on channels. Hence, by reading from that channel we know when the
  ;; results have arrived.
  (go
    (let [;; Make sure the edges of the window are between 1 and last-page-no
          start-page (max (- centre-page-no result-window-halfsize) 1)
          end-page   (min (+ centre-page-no result-window-halfsize) (last-page-no @total))
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

        (let [;; Register the pages as being fetched
              _            (swap! fetching-pages #(apply conj % page-nos))
              ;; Calculate the first and last result index (zero-based) to request from the server
              first-result (* page-size (dec (first page-nos)))
              last-result  (dec (* page-size (last page-nos)))
              results-ch   (http/get "results" {:query-params {:corpus-id    (:id @corpus)
                                                               :search-id    (:id @search)
                                                               :start        first-result
                                                               :end          last-result
                                                               :cpu-counts   (str (vec @cpu-counts))
                                                               :context-size @context-size
                                                               :sort-key     (name @sort-key)}})
              ;; Park until results are available on the core.async channel
              {:keys [status success] req-result :body} (<! results-ch)]
          (when (= status 401)
            (reset! (:authenticated-user m) nil))
          ;; Remove the pages from the set of pages currently being fetched
          (swap! fetching-pages #(apply disj % page-nos))
          (if-not success
            (.log js/console status)
            ;; Add the fetched pages to the top-level results ratom
            (swap! results merge (zipmap page-nos
                                         (partition-all page-size
                                                        (map (partial cleanup-result m)
                                                             req-result)))))
          ;; This keyword will be put on the channel returned by the go block
          ;; and hence the function
          ::fetched-pages)))))

(defn- sort-button [{{:keys [total sort-key]} :results-view
                     :keys                    [searching?] :as a} m]
  (let [sk        @sort-key
        on-select (fn [_ event-key]
                    (reset! sort-key (keyword event-key))
                    (reset-results! a m)
                    (fetch-result-window! a m 1))]
    [b/dropdownbutton {:id       "sort-button"
                       :title    "Sort"
                       :bs-size  "small"
                       :disabled (or @searching?
                                     (nil? @total)
                                     (zero? @total))
                       :style    {:margin-bottom 10}}
     [b/menuitem {:event-key :position, :on-select on-select}
      (when (= sk :position) [b/glyphicon {:glyph "ok"}]) "  By corpus position"]
     [b/menuitem {:event-key :match, :on-select on-select}
      (when (= sk :match) [b/glyphicon {:glyph "ok"}]) "  By match"]
     [b/menuitem {:event-key :left-immediate, :on-select on-select}
      (when (= sk :left-immediate) [b/glyphicon {:glyph "ok"}]) "  By immediate left context"]
     [b/menuitem {:event-key :left-wide, :on-select on-select}
      (when (= sk :left-wide) [b/glyphicon {:glyph "ok"}]) "  By wider left context (10 tokens)"]
     [b/menuitem {:event-key :right-immediate, :on-select on-select}
      (when (= sk :right-immediate) [b/glyphicon {:glyph "ok"}]) "  By immediate right context"]
     [b/menuitem {:event-key :right-wide, :on-select on-select}
      (when (= sk :right-wide) [b/glyphicon {:glyph "ok"}]) "  By wider right context (10 tokens)"]]))

(defn- download-button [{{:keys [showing-download-popup? total]} :results-view :keys [searching?]}]
  [b/button {:id       "download-button"
             :bs-size  "small"
             :bs-style "default"
             :disabled (or @searching?
                           (nil? @total)
                           (zero? @total))
             :on-click #(reset! showing-download-popup? true)}
   "Download"])

(defn- download-popup
  [{{:keys [cpu-counts context-size showing-download-popup? downloading?]} :results-view}
   {:keys [corpus search] :as m}]
  (r/with-let
    [hide-popup #(reset! showing-download-popup? false)
     attrs (all-displayed-attrs corpus)
     form-field-vals (r/atom {:format   "excel"
                              :headers? true
                              :attrs    (zipmap attrs (cons true (repeat false)))})
     download (fn [_]
                (reset! downloading? true)
                (go
                  (let [{:keys [format headers? attrs]} @form-field-vals
                        results-ch (http/post "download-results"
                                              {:json-params {:corpus-id    (:id @corpus)
                                                             :search-id    (:id @search)
                                                             :cpu-counts   (vec @cpu-counts)
                                                             :format       format
                                                             :headers?     headers?
                                                             :attrs        (keep
                                                                             (fn [[k v]]
                                                                               (when v (first k)))
                                                                             attrs)
                                                             :context-size @context-size}})
                        results    (<! results-ch)
                        {file-url :body} results]
                    (reset! downloading? false)
                    (reset! showing-download-popup? false)
                    (if (= (:status results) 401)
                      (reset! (:authenticated-user m) nil)
                      (aset js/window "location" file-url)))))]
    (let [attr-boxes (doall
                       (map-indexed (fn [index [attr-code attr-name :as attr]]
                                      ^{:key attr-code}
                                      [b/checkbox
                                       {:inline    true
                                        :style     (when (zero? index) {:margin-left 10})
                                        :checked   (get-in @form-field-vals [:attrs attr])
                                        :on-change (fn [e]
                                                     (swap! form-field-vals assoc-in [:attrs attr]
                                                            (.-target.checked e)))}
                                       attr-name])
                                    attrs))]
      [b/modal {:class-name "download-modal"
                :show       @showing-download-popup?
                :on-hide    hide-popup}
       [b/modalheader {:close-button true}
        [b/modaltitle "Download results"]]
       [b/modalbody
        [:div {:style {:display "inline-block"}}
         [b/checkbox {:checked   (:headers? @form-field-vals)
                      :on-change #(swap! form-field-vals assoc :headers? (.-target.checked %))}
          "Create headers?"]]
        [:div {:style {:display "inline-block" :margin-left 20}}
         [b/formgroup
          [b/formcontrol {:component-class "select"
                          :value           (get @form-field-vals :format)
                          :on-change       #(swap! form-field-vals
                                                   assoc :format (.-target.value %))}
           [:option {:value "excel"} "Excel (max 50,000 results)"]
           [:option {:value "tsv"} "Tab-separated values (.tsv)"]
           [:option {:value "csv"} "Comma-separated values (.csv)"]]]]
        [b/panel {:header "Attributes"}
         [:form {:on-submit (fn [e]
                              (.preventDefault e)
                              (download @form-field-vals))}
          [b/formgroup
           attr-boxes]]]]
       [b/modalfooter
        [:div {:style {:display "inline-block" :margin-right 5}}
         [spinner-overlay {:spin? @downloading?}
          [b/button {:bs-style "success" :on-click download :disabled @downloading?} "Download"]]]
        [b/button {:on-click hide-popup} "Close"]]])))

(defn- statistics-button [{{:keys [freq-attr freq-attr-sorted freq-res]} :results-view :as a}
                          {:keys [corpus] :as m}]
  (let [on-checkbox-changed (fn [event attr]
                              (if (.-target.checked event)
                                (swap! freq-attr conj attr)
                                (swap! freq-attr disj attr)))
        attr-checkbox       (fn [[id name]]
                              ^{:key id}
                              [:label.checkbox-inline {:style {:padding-left 0 :padding-right 18}}
                               [:input {:name      id
                                        :type      "checkbox"
                                        :style     {:margin-left -18}
                                        :on-change #(on-checkbox-changed % id)}] name])]
    [:div
     [:div.checkbox {:style {:display "table-cell" :padding-top 7 :padding-left 10}}
      (cons ^{:key "spacer"} [:div {:style {:display "inline-block" :width 10}}]
            (map attr-checkbox (all-displayed-attrs corpus)))]
     [b/button {:style {:margin-top 5 :margin-bottom 10} :on-click #(stats! a m)} "Update stats"]
     [b/table {:bordered true}
      [:tbody
       [:tr
        [:th "Count"]
        (doall
          (map (fn [attr] ^{:key (first attr)} [:th (->> attr second name)]) @freq-attr-sorted))]
       (when (seq? @freq-res)
         (let [process-col (fn [index col]
                             ^{:key index}
                             [:td (str/replace col #"^__UNDEF__$" "")])
               process-row (fn [index row]
                             ^{:key index}
                             [:tr (map-indexed process-col (str/split (str/replace-first row #"^(\d+)\s+" "$1\t") #"\t"))])]
           (map-indexed process-row (take 2500 @freq-res))))]]
     (when (string? @freq-res) [spinner-overlay {:spin? true :width 45 :height 45 :top 5} [:div @freq-res]])]))

(defn- context-size-selector [{{:keys [results
                                       context-size
                                       page-no
                                       fetching-pages]} :results-view :as a}
                              m]
  [:div.pull-right {:style {:font-size 13 :display "inline-block" :margin-right 15 :margin-top 3}}
   "Context: "
   [:input.form-control.input-sm
    {:type        "text"
     :style       {:display "inline-block" :width 28 :height 25 :padding 5}
     :value       @context-size
     :on-click    #(.select (.-target %))
     :on-change   #(reset! context-size (.-target.value %))
     :on-key-down (fn [e]
                    (when (= "Enter" (.-key e))
                      ;; Remove all the result pages we have fetched so far, since they need
                      ;; to be re-fetched with the new context size.
                      (reset! results nil)
                      ;; Ignore any result page requests that may be currently in flight
                      (reset! fetching-pages #{})
                      ;; Fetch the current result page using the new context size
                      (fetch-result-window! a m @page-no)))}]
   " words"])


(defn- pagination [{{:keys [results total page-no paginator-page-no
                            paginator-text-val fetching-pages]} :results-view :as a} m]
  (r/with-let [set-page (fn [e n fetching?]
                          (.preventDefault e)
                          ;; Don't allow switching to a new page while we are in the processing of
                          ;; fetching one or more pages, since the user may start clicking lots of
                          ;; times, generating lots of concurrent requests
                          (when-not fetching?
                            (let [new-page-no (js/parseInt n)
                                  last-page   (last-page-no @total)]
                              (when (<= 1 new-page-no last-page)
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
                                    ;; If necessary, fetch any result pages in a window centred
                                    ;; around the selected page in order to speed up pagination
                                    ;; to nearby pages. No need to wait for it to finish though.
                                    (fetch-result-window! a m new-page-no))
                                  (go
                                    ;; Otherwise, we need to park until the results from the server
                                    ;; arrive before setting the page to be shown in the result
                                    ;; table
                                    (<! (fetch-result-window! a m new-page-no))
                                    ;; Don't show the fetched page if we have already selected
                                    ;; another page in the paginator while we were waiting for
                                    ;; the request to finish
                                    (when (= new-page-no @paginator-page-no)
                                      (reset! page-no new-page-no))))))))
               on-key-down (fn [e fetching?]
                             (when (= "Enter" (.-key e))
                               (if (str/blank? @paginator-text-val)
                                 ;; If the text field is blank when we hit Enter, set its value
                                 ;; to the last selected page number instead
                                 (reset! paginator-text-val @paginator-page-no)
                                 ;; Otherwise, make sure the entered number is within valid
                                 ;; bounds and set it to be the new selected page
                                 (let [last-page (last-page-no @total)
                                       new-val   (as-> @paginator-text-val v
                                                       (js/parseInt v)
                                                       (if (pos? v) v 1)
                                                       (if (<= v last-page) v last-page))]
                                   (set-page e new-val fetching?)))))]
    (let [fetching? (seq @fetching-pages)]
      (when (> @total page-size)
        [:div.pull-right
         [:nav
          [:ul.pagination.pagination-sm
           [:li {:class-name (when (or (= @paginator-page-no 1) fetching?)
                               "disabled")}
            [:a {:href       "#"
                 :aria-label "First"
                 :title      "First"
                 :on-click   #(set-page % 1)}
             [:span {:aria-hidden "true"} "«"]]]
           [:li {:class-name (when (or (= @paginator-page-no 1) fetching?)
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
              :on-key-down #(on-key-down % fetching?)}]]
           [:li {:class-name (when (or (= @paginator-page-no (last-page-no @total)) fetching?)
                               "disabled")}
            [:a {:href       "#"
                 :aria-label "Next"
                 :title      "Next"
                 :on-click   #(set-page % (inc @paginator-page-no))}
             [:span {:aria-hidden "true"} "›"]]]
           [:li {:class-name (when (or (= @paginator-page-no (last-page-no @total)) fetching?)
                               "disabled")}
            [:a {:href       "#"
                 :aria-label "Last"
                 :title      "Last"
                 :on-click   #(set-page % (last-page-no @total))}
             [:span {:aria-hidden "true"} "»"]]]]]]))))

(defn- concordance-toolbar [a {:keys [corpus] :as m}]
  [:div.row {:style {:margin-top 15}}
   [:div.col-sm-12
    [b/buttontoolbar {:style {:height 44}}
     #_[sort-button a m]
     [download-button a m]
     [download-popup a m]
     [pagination a m]
     (when-not (= (:search-engine @corpus) "cwb_speech")
       [context-size-selector a m])]]])

(defmulti result-links
  "Multimethod for links shown to the left of each search result in a
  concordance table. It accepts a model/domain state map and the result as
  arguments and dispatches to the correct method based on the value of :code in
  the corpus map found in the model/domain state map. The :default case returns
  nil."
  (fn [_ {corpus :corpus} _ _] (:code @corpus)))

(defmethod result-links :default [_ _ _ _] nil)

(defmulti show-texts-extra-col-name
  "Multimethod for the name of an extra corpus-specific column to be displayed
  in the show-texts popup."
  (fn [corpus] (:code @corpus)))

(defmulti show-texts-extra-col-comp
  "Multimethod for the data in an extra corpus-specific column to be displayed
  in the show-texts popup, given a particular page of corpus texts."
  (fn [corpus] (:code @corpus)))

(defmethod show-texts-extra-col-name :default [_] nil)
(defmethod show-texts-extra-col-comp :default [_] nil)

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
     [pagination a m]]]])

(defn- geo-map-colorpicker [selected-color color]
  [:button.btn.btn-xs.colorpicker
   {:style    {:background-color (name color)
               :border-color     (if (#{:white :red :orange :yellow} color) "black" "red")
               :border-width     (if (= @selected-color color) "4px" "1px")}
    :on-click #(reset! selected-color color)}])

(defn- geo-map [{{{:keys [geo-data colored-phons selected-color]} :geo-map} :results-view}
                {:keys [corpus]}
                view-type]
  (r/with-let
    [geo-map-rendered? (atom false)]
    ;; react-bootstrap renders and mounts the contents of all tabs immediately (i.e., no
    ;; lazy rendering), but instantiating a Google Map while its container is hidden doesn't
    ;; work. Hence, we don't render the GeoDistributionMap component unless the use has
    ;; actually selected the geo-map tab at least once.
    (when (or @geo-map-rendered? (= @view-type "geo-map"))
      (reset! geo-map-rendered? true)
      [:div.geo-map {:style {:margin-top 4}}
       [:div {:style {:padding "5px 5px 5px 0" :margin-right 4 :float "left"}}
        (for [color geo-map-colors]
          ^{:key color} [geo-map-colorpicker selected-color color])]
       [:div {:style {:padding "5px 5px 5px 0" :float "left"}}
        (doall (for [phon (sort (keys @geo-data))]
                 (let [[c _] (first (filter #(get (second %) phon) @colored-phons))
                       dark-colors #{:green :blue :purple :black :red}
                       style       (when c
                                     {:color            (if (get dark-colors c) "white" "black")
                                      :background-color (name c)
                                      :background-image "none"
                                      :text-shadow      "0 -1px 0 rgba(0,0,0,.2)"})
                       total       (reduce #(+ %1 (second %2)) 0 (get @geo-data phon))
                       btn-tooltip (apply str (conj
                                                (mapv (fn [[location num]]
                                                        (str location ": " num "; "))
                                                      (reverse (sort-by second
                                                                        (get @geo-data phon))))
                                                "<br><b>Total: " total "</b>"))]
                   ^{:key phon}
                   [b/button {:bs-size     "xsmall"
                              :class-name  "phon-button"
                              :data-toggle "tooltip"
                              :title       btn-tooltip
                              :data-html   true
                              :style       style
                              :on-click    (fn [e]
                                             ;; Blur button to prevent tooltip from sticking
                                             (.blur (js/$ (.-target e)))
                                             ;; If the button already has a colour, remove it
                                             ;; (regardless of whether another colour picker has
                                             ;; been selected or not, this colour should be removed)
                                             (when c
                                               (swap! colored-phons update c disj phon))
                                             ;; If a colour picker has been selected and if differs
                                             ;; from the current colour of the button, set it to
                                             ;; be the new button colour.
                                             (when (and @selected-color (not= @selected-color c))
                                               (swap! colored-phons update @selected-color
                                                      (fn [phons]
                                                        (conj phons phon)))))}
                    phon])))]
       [:div {:style {:clear "both"}}
        (let [all-coords      (:geo-coords @corpus)
              loc-names       (distinct (mapcat (fn [[_ v]] (keys v)) @geo-data))
              ;; Convert the hash map with frequency distribution over locations per phon
              ;; to one with the frequency distribution over phons per locations
              loc-phon-freqs  (->> @geo-data
                                   (mapcat (fn [[phon loc-freqs]]
                                             (map (fn [[loc freq]]
                                                    [loc phon freq])
                                                  loc-freqs)))
                                   (group-by first)
                                   (map (fn [[loc loc-phon-freqs]]
                                          [loc (into {} (map (fn [[_ phon freq]]
                                                               [phon freq])
                                                             loc-phon-freqs))]))
                                   (into {}))
              coords          (map (fn [loc-name]
                                     {:name   loc-name
                                      :coords (->> loc-name keyword (get all-coords))
                                      :phons  (->> loc-name
                                                   (get loc-phon-freqs)
                                                   (map (fn [[phon freq]]
                                                          (str phon ": " freq)))
                                                   (str/join "; "))})
                                   loc-names)
              ;; These are the small red dots that mark all locations where hits were found
              small-dots      (map (fn [{name :name [lat lng] :coords phons :phons}]
                                     {:latitude  lat
                                      :longitude lng
                                      :name      name
                                      :label     (str name ": " phons)})
                                   coords)
              ;; Now find, for each colour in the colour picker, those locations where we
              ;; found one or more of the phonetic forms selected for that colour, and create
              ;; coloured markers for them.
              selected-points (apply
                                concat
                                (for [[color phons] @colored-phons
                                      phon (seq phons)
                                      :let [location-freqs     (get @geo-data phon)
                                            selected-locations (set (keys location-freqs))
                                            selected-coords    (filter #(get selected-locations
                                                                             (:name %))
                                                                       small-dots)]]
                                  (map (fn [coord-map]
                                         (assoc coord-map
                                           :icon (str "img/speech/mm_20_" (name color) ".png")))
                                       selected-coords)))
              points          (concat small-dots selected-points)]
          [:> js/GeoDistributionMap {:initLat  64
                                     :initLon  3
                                     :initZoom 4
                                     :width    640
                                     :height   460
                                     :points   points}])]])))

(defn results [{:keys                       [searching? num-resets]
                {:keys [view-type results]} :results-view :as a}
               {:keys [corpus] :as m}]
  [:div
   [:div.row
    [top-toolbar a m]]
   ^{:key @num-resets} [search-inputs a m] ; See comments in cglossa.start
   [spinner-overlay {:spin? (and @searching? (empty? @results)) :width 45 :height 45 :top 75}
    [b/tabs {:id         "result-tabs"
             :style      {:margin-top 15}
             :animation  false
             :active-key @view-type
             :on-select  #(reset! view-type %)}
     [b/tab {:title "Concordance" :event-key :concordance}
      [results-info a]
      [concordances a m]]
     (when (:geo-coords @corpus)
       [b/tab {:title "Map" :event-key :geo-map}
        [results-info a]
        [geo-map a m view-type]])
     [b/tab {:title "Statistics" :event-key :statistics :disabled false}
      [results-info a]
      [:div.row {:style {:margin-top 15}}
       [:div.col-sm-12
        [b/buttontoolbar {:style {:height 44}}
         [statistics-button a m]]]]]]]])
