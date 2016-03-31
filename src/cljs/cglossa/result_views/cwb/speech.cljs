(ns cglossa.result-views.cwb.speech
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [concordance-table]]
            [cglossa.result-views.cwb.shared :as shared]
            react-jplayer)
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- toggle-player [index player-type media-type
                      {{{:keys [showing-media-popup media-obj player-row-index
                                current-player-type current-media-type]} :media} :results-view}
                      {:keys [corpus search]}]
  (let [row-no         (when-not (and (= index @player-row-index)
                                      (= player-type @current-player-type)
                                      (= media-type @current-media-type))
                         index)
        new-media-type (when row-no
                         media-type)]
    (go
      (let [response (<! (http/get "play-video" {:query-params {:corpus-code  (:code @corpus)
                                                                :search-id    (:id @search)
                                                                :result-index index
                                                                :context-size 7}}))]
        (reset! showing-media-popup true)
        (reset! media-obj (get-in response [:body :media-obj]))
        (reset! player-row-index row-no)
        (reset! current-player-type player-type)
        (reset! current-media-type new-media-type)))))

(defn- extract-fields [res]
  (let [m (re-find #"^<who_name\s+(\S*?)>:\s+(.*)\{\{(.+?)\}\}(.*?)$" res)]
    (let [[_ s-id pre match post] m
          ;; If the result begins with a who_name tag with the same ID as the one for the
          ;; actual match, it feels reduntant (since that speaker ID is listed just
          ;; to the left of it), so just remove it.
          pre*   (str/replace pre (re-pattern (str "^<who_name\\s+" s-id ">")) "")
          ;; Do the same with the match if there is no left context
          match* (if (str/blank? pre)
                   (str/replace match (re-pattern (str "^<who_name\\s+" s-id ">")) "")
                   match)]
      [s-id [pre* match* post]])))

(defn- orthographic-row [a {:keys [corpus] :as m} result]
  ^{:key (str "ort" (hash result))}
  [:tr
   (shared/id-column m result)
   (shared/text-columns result)])

(defn- phonetic-row [a {:keys [corpus] :as m} result row-index]
  (let [audio? (:audio? @corpus)
        video? (:video? @corpus)]
    ^{:key (str "phon" (hash result))}
    [:tr
     [:td {:style {:vertical-align "middle"}}
      [:nobr
       (when video?
         [b/button {:bs-size  "xsmall" :title "Show video"
                    :on-click #(toggle-player row-index "jplayer" "video" a m)}
          [b/glyphicon {:glyph "film"}]])
       (when audio?
         (list ^{:key :audio-btn}
               [b/button {:bs-size  "xsmall" :title "Play audio" :style {:margin-left 2}
                          :on-click #(toggle-player row-index "jplayer" "audio" a m)}
                [b/glyphicon {:glyph "volume-up"}]]
               ^{:key :waveform-btn}
               [b/button {:bs-size  "xsmall" :title "Show waveform" :style {:margin-left 2}
                          :on-click #(toggle-player row-index "wfplayer" "audio" a m)}
                [:img {:src "img/waveform.png" :style {:width 12}}]]))]]
     (shared/text-columns result)]))

(defn- separator-row [result]
  ^{:key (str "sep" (hash result))}
  [:tr {:style {:background-color "#f1f1f1"}}
   [:td {:col-span 4 :style {:padding 3}}]])

(defn- extra-row [result attr {:keys [corpus]}]
  (let [sound?       (:has-sound @corpus)
        video?       (:has-video @corpus)
        match        (first (filter (fn [_ v] (:is-match v))
                                    (get-in result [:media-obj :divs :annotation])))
        row-contents (str/join " " (for [[_ v] (:line match)]
                                     (get v attr)))]
    [:tr
     (when (:s-id result)
       [:td])
     (when (or sound? video?)
       [:td.span1])
     [:td {:col-span 3}
      row-contents]]))

(defn- process-token [token index displayed-field-index tip-field-indexes]
  (when-not (str/blank? token)
    (let [attrs     (str/split token #"/")
          tip-attrs (->> tip-field-indexes
                         (map #(nth attrs %))
                         (remove #(get #{"__UNDEF__" "-"} %)))
          tip-text  (str/join " " (-> tip-attrs vec
                                      ;; Show the (orthographic or phonetic) form in italics
                                      (update 0 #(str "<i>" % "</i>"))
                                      ;; Show the lemma in quotes
                                      (update 1 #(str "\"" % "\""))))]
      ^{:key index}
      [:span {:data-toggle "tooltip"
              :title       tip-text
              :data-html   true}
       (nth attrs displayed-field-index) " "])))

(defn- process-field [displayed-field-index tip-field-indexes field]
  "Processes a pre-match, match, or post-match field."
  (let [tokens (-> field
                   (str/replace #"<who_name\s+(.+?)>\s*" "<who_name_$1> ")
                   (str/replace #"\s*</who_name>" " $&")
                   (str/split #"\s+"))]
    (keep-indexed (fn [index token]
                    (if-let [[_ speaker-id] (re-find #"<who_name_(.+?)>" token)]
                      ;; Extract the speaker ID and put it in front of its segment
                      ^{:key index} [:span.speaker-id (str "<" speaker-id ">") " "]
                      ;; Ignore end-of-segment tags; process all other tokens
                      (when-not (re-find #"</who_name>" token)
                        (process-token token index displayed-field-index tip-field-indexes))))
                  tokens)))

(defn single-result-rows [a m ort-index phon-index ort-tip-indexes phon-tip-indexes res index]
  "Returns one or more rows representing a single search result."
  (let [line         (first (:text res))
        [s-id fields] (extract-fields line)
        [ort-pre ort-match ort-post] (map (partial process-field ort-index ort-tip-indexes)
                                          fields)
        [phon-pre phon-match phon-post] (map (partial process-field phon-index phon-tip-indexes)
                                             fields)
        res-info     {:ort  {:s-id       s-id
                             :pre-match  ort-pre
                             :match      ort-match
                             :post-match ort-post}
                      :phon {:s-id       s-id
                             :pre-match  phon-pre
                             :match      phon-match
                             :post-match phon-post}}
        orthographic (orthographic-row a m (:ort res-info))
        phonetic     (phonetic-row a m (:phon res-info) index)
        separator    (separator-row res-info)]
    (list orthographic phonetic separator)))

(defmethod concordance-table "cwb_speech"
  [{{:keys [results page-no] {:keys [showing-media-popup
                                     media-obj
                                     player-row-index
                                     current-player-type
                                     current-media-type]} :media} :results-view :as a}
   {:keys [corpus] :as m}]
  (let [res         (get @results @page-no)
        hide-player (fn []
                      (reset! showing-media-popup false)
                      (reset! player-row-index nil)
                      (reset! current-player-type nil)
                      (reset! current-media-type nil))]
    [:span
     (when @showing-media-popup
       [b/modal {:class-name "media-popup"
                 :show       true
                 :on-hide    hide-player
                 :on-enter   (fn [node]
                               ;; Set the width of the popup to almost that of the window
                               (.width (.find (js/$ node) ".modal-dialog")
                                       (- (.-innerWidth js/window) 40)))}
        [b/modalheader {:close-button true}
         [b/modaltitle "Video"]]
        [b/modalbody (condp = @current-player-type
                       "jplayer"
                       [:> js/Jplayer {:media-obj  @media-obj
                                       :media-type @current-media-type
                                       :ctx_lines  (:initial-context-size @corpus 1)}]

                       "wfplayer"
                       [:WFplayer {:media-obj @media-obj}])]
        [b/modalfooter
         [b/button {:on-click hide-player} "Close"]]])
     [:div.row>div.col-sm-12.search-result-table-container
      [b/table {:bordered true}
       [:tbody
        (let [attrs             (-> @corpus :languages first :config :displayed-attrs)
              ort-index         0       ; orthographic form is always the first attribute
              ;; We need to inc phon-index and lemma-index since the first attribute ('word') is
              ;; not in the list because it is shown by default by CQP
              phon-index        (first (keep-indexed #(when (= %2 :phon) (inc %1)) attrs))
              lemma-index       (first (keep-indexed #(when (= %2 :lemma) (inc %1)) attrs))
              remaining-indexes (remove #(#{ort-index phon-index lemma-index} %)
                                        (range (count attrs)))
              ort-tip-indexes   (into [phon-index lemma-index] remaining-indexes)
              phon-tip-indexes  (into [ort-index lemma-index] remaining-indexes)]
          (doall (map (partial single-result-rows a m
                               ort-index phon-index ort-tip-indexes phon-tip-indexes)
                      res
                      (range (count res)))))]]]]))
