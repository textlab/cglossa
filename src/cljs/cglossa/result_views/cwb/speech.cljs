(ns cglossa.result-views.cwb.speech
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [concordance-table]]
            [cglossa.result-views.cwb.shared :as shared]
            react-jplayer))

(defn- toggle-player [index player-type media-type
                      {{{:keys [player-row-index
                                current-player-type current-media-type]} :media} :results-view}]
  (let [row-no         (when-not (and (= index @player-row-index)
                                      (= player-type @current-player-type)
                                      (= media-type @current-media-type))
                         index)
        new-media-type (when row-no
                         media-type)]
    (reset! player-row-index row-no)
    (reset! current-player-type player-type)
    (reset! current-media-type new-media-type)))

(defn- extract-fields [res]
  (let [m (re-find #"^<who_name\s+(.*?)>:\s+(.*)\{\{(.+?)\}\}(.*?)$" res)]
    (let [[_ s-id pre match post] m]
      [(str/trim s-id) [pre match post]])))

(defn- main-row [result index a {:keys [corpus] :as m}]
  (let [sound? (:has-sound @corpus)
        video? (:has-video @corpus)]
    ^{:key (hash result)}
    [:tr
     (if (or sound? video?)
       [:td.span1
        (when video?
          [b/button {:bs-size  "xsmall" :title "Show video" :style {:width "100%" :margin-bottom 3}
                     :on-click #(toggle-player index "jplayer" "video" a)}
           [b/glyphicon {:glyph "film"}]])
        (when sound?
          (list ^{:key :audio-btn}
                [b/button {:bs-size  "xsmall" :title "Play audio" :style {:width "100%"}
                           :on-click #(toggle-player index "jplayer" "audio" a)}
                 [b/glyphicon {:glyph "volume-up"}]]
                ^{:key :waveform-btn}
                [b/button {:bs-size  "xsmall" :title "Show waveform" :style {:width "100%"}
                           :on-click #(toggle-player index "wfplayer" "audio" a)}
                 [:img {:src "img/waveform.png"}]]))])
     (shared/id-column m result)
     (shared/text-columns result)]))

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

(defn- process-token [token index]
  (let [attrs    (str/split token #"/")
        tip-text (str/join " " (->> attrs rest
                                    (remove #(get #{"__UNDEF__" "-"} %))))]
    ^{:key index}
    [:span {:data-toggle "tooltip"
            :title       tip-text}
     (first attrs) " "]))

(defn- process-field [field]
  "Processes a pre-match, match, or post-match field."
  (let [tokens (-> field
                   (str/replace #"<who_name\s+(.+?)>" "<who_name_$1> ")
                   (str/replace "</who_name>" " $&")
                   (str/split #"\s+"))]
    (map-indexed (fn [index token]
                   (if-let [[_ speaker-id] (re-find #"<who_name_(.+?)>" token)]
                     ;; Extract the speaker ID and put it in front of its segment
                     ^{:key index} [:span.speaker-id speaker-id " "]
                     ;; Ignore end-of-segment tags; process all other tokens
                     (when-not (re-find #"</who_name>" token)
                       (process-token token index))))
                 tokens)))

(defn single-result-rows [a m res index]
  "Returns one or more rows representing a single search result."
  (let [[main-line & other-lines] (:text res)
        [s-id fields] (extract-fields main-line)
        [pre match post] (map process-field fields)
        res-info {:s-id       s-id
                  :pre-match  pre
                  :match      match
                  :post-match post}
        main     (main-row res-info index a m)
        ;others   (map-indexed non-first-multilingual other-lines)
        ]
    ;(cons main others)
    main
    ))

(defmethod concordance-table "cwb_speech"
  [{{:keys [results page-no] {:keys [player-row-index
                                     current-player-type
                                     current-media-type]} :media} :results-view :as a}
   {:keys [corpus] :as m}]
  (let [res         (get @results @page-no)
        hide-player (fn []
                      (reset! player-row-index nil)
                      (reset! current-player-type nil)
                      (reset! current-media-type nil))]
    [:span
     (when @player-row-index
       (let [media-obj (:media-obj (nth res @player-row-index))]
         [b/modal {:show    true
                   :on-hide hide-player}
          [b/modalheader {:close-button true}
           [b/modaltitle "Video"]]
          [b/modalbody (condp = @current-player-type
                         "jplayer"
                         [:tr
                          [:td {:col-span 10}
                           [:> js/Jplayer {:media-obj  media-obj
                                           :media-type @current-media-type
                                           :ctx_lines  (:initial-context-size @corpus 1)}]]]

                         "wfplayer"
                         [:tr
                          [:td {:col-span 10}
                           [:WFplayer {:media-obj media-obj}]]])]
          [b/modalfooter
           [b/button {:on-click hide-player} "Close"]]]))
     [:div.row>div.col-sm-12.search-result-table-container
      [b/table {:striped true :bordered true}
       [:tbody
        (doall (map (partial single-result-rows a m)
                    res
                    (range (count res))))]]]]))
