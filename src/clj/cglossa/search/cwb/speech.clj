(ns cglossa.search.cwb.speech
  "Support for speech corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [korma.core :as korma :refer [defentity table entity-fields select where]]
            [cglossa.db.corpus :refer [get-corpus]]
            [cglossa.search.core :refer [run-queries get-results transform-results
                                         geo-distr-queries]]
            [cglossa.search.cwb.shared :refer [cwb-query-name cwb-corpus-name run-cqp-commands
                                               construct-query-commands position-fields
                                               displayed-attrs-command aligned-languages-command
                                               sort-command]]))

(defentity media-file (table :media_file) (entity-fields :basename))

;; TODO: Fetch these from the definition of the tag set for the tagger that is being used
(def ^:private display-attrs [:lemma :phon :pos :gender :num :type :defn
                              :temp :pers :case :degr :descr :nlex :mood :voice])


(defmethod position-fields "cwb_speech" [_ positions-filename]
  (korma/raw (str "replace(replace(`bounds`, '-', '\t'), ':', '\n') INTO OUTFILE '"
                  positions-filename "' FIELDS ESCAPED BY ''")))


(defmethod run-queries "cwb_speech" [corpus search-id queries metadata-ids _
                                     page-size _ sort-key]
  (let [named-query (cwb-query-name corpus search-id)
        startpos    0
        endpos      (get-in corpus [:extra-info :size (:code corpus)])
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (construct-query-commands corpus queries metadata-ids named-query
                                               search-id startpos endpos
                                               :s-tag "sync_time")
                     (str "save " named-query)
                     (str "set Context 1 sync_time")
                     "set PrintStructures \"who_name\""
                     "set LD \"{{\""
                     "set RD \"}}\""
                     (displayed-attrs-command corpus queries)
                     "show +who_name"
                     ;; Return the total number of search results...
                     "size Last"
                     ;; ...as well as two pages of actual results
                     (str "cat Last 0 " (dec (* 2 page-size)))]
        [hits cnt-str] (run-cqp-commands corpus (filter identity (flatten commands)) true)
        cnt         (Integer/parseInt cnt-str)]
    ;; Since we dont't currently do multi-cpu processing of speech corpora,
    ;; the sequence of counts only contains the count we get by the single cpu.
    [hits cnt [cnt]]))


(defmethod get-results "cwb_speech" [corpus search queries start end _ sort-key]
  (let [named-query (cwb-query-name corpus (:id search))
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (str "set Context 1 sync_time")
                     "set PrintStructures \"who_name\""
                     "set LD \"{{\""
                     "set RD \"}}\""
                     (displayed-attrs-command corpus queries)
                     (sort-command named-query sort-key)
                     (str "cat " named-query " " start " " end)]]
    (run-cqp-commands corpus (flatten commands) false)))


(defn- fix-brace-positions [result]
  ;; If the matching word/phrase is at the beginning of the segment, CQP puts the braces
  ;; marking the start of the match before the starting segment tag
  ;; (e.g. {{<turn_endtime 38.26><turn_starttime 30.34>went/go/PAST>...). Probably a
  ;; bug in CQP? In any case we have to fix it by moving the braces to the
  ;; start of the segment text instead. Similarly if the match is at the end of a segment.
  (-> result
      (str/replace #"\{\{((?:<\S+?\s+?\S+?>\s*)+)" ; Find start tags with attributes
                   "$1{{")              ; (i.e., not the match)
      (str/replace #"((?:</\S+?>\s*)+)\}\}" ; Find end tags
                   "}}$1")))


(defn- find-timestamps [result]
  (for [[segment start end] (re-seq #"<sync_time\s+([\d\.]+)><sync_end\s+([\d\.]+)>.*?</sync_time>"
                                    result)
        :let [num-speakers (count (re-seq #"<who_name" segment))]]
    ;; Repeat the start and end time for each speaker within the same segment
    [(repeat num-speakers start) (repeat num-speakers end)]))


(defn- build-annotation [index line speaker starttime endtime]
  (let [match? (boolean (re-find #"\{\{" line))
        line*  (str/replace line #"\{\{|\}\}" "")
        tokens (str/split line* #"\s+")]
    [index {:speaker speaker
            :line    (into {} (map-indexed (fn [index token]
                                             (let [attr-values (str/split token #"/")]
                                               [index (zipmap (cons "word" display-attrs)
                                                              attr-values)]))
                                           tokens))
            :from    starttime
            :to      endtime
            :match?  match?}]))


(defn- create-media-object
  "Creates the data structure that is needed by jPlayer for a single search result."
  [overall-starttime overall-endtime starttimes endtimes lines speakers corpus line-key]
  (let [annotations         (into {} (map build-annotation
                                          (range) lines speakers
                                          starttimes endtimes))
        matching-line-index (first (keep-indexed #(when (re-find #"\{\{" %2) %1) lines))
        last-line-index     (dec (count lines))
        movie-loc           (-> media-file
                                (select (where (between line-key
                                                        [:line_key_begin :line_key_end])))
                                first
                                :basename)]
    {:title             ""
     :last-line         last-line-index
     :display-attribute "word"
     :corpus-id         (:id corpus)
     :mov               {:supplied  "m4v"
                         :path      (str "media/" (:code corpus))
                         :movie-loc movie-loc
                         :line-key  line-key
                         :start     overall-starttime
                         :stop      overall-endtime}
     :divs              {:annotation annotations}
     :start-at          matching-line-index
     :end-at            matching-line-index
     :min-start         0
     :max-end           last-line-index}))


(defn- extract-media-info [corpus result]
  (let [result*           (fix-brace-positions result)
        timestamps        (find-timestamps result*)
        starttimes        (mapcat first timestamps)
        endtimes          (mapcat last timestamps)
        overall-starttime (first starttimes)
        overall-endtime   (last endtimes)
        speakers          (map second (re-seq #"<who_name\s+(.+?)>" result*))
        ;; All line keys within the same result should point to the same media file,
        ;; so just find the first one.
        line-key          (second (re-find #"<who_line_key\s+(\d+)>" result*))]
    (let [media-obj-lines (map second (re-seq (if line-key
                                                #"<who_line_key.+?>(.*?)</who_line_key>"
                                                #"<who_name.+?>(.*?)</who_name>")
                                              result*))]
      {:media-obj (create-media-object overall-starttime overall-endtime starttimes endtimes
                                       media-obj-lines speakers corpus line-key)
       :line-key  line-key})))


(defn play-video [corpus-id search-id result-index context-size]
  (let [corpus      (get-corpus {:id corpus-id})
        named-query (cwb-query-name corpus search-id)
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (str/upper-case (:code corpus))
                     (str "set Context " context-size " sync_time")
                     "set LD \"{{\""
                     "set RD \"}}\""
                     "show +sync_time +sync_end +who_name +who_line_key"
                     (str "cat " named-query " " result-index " " result-index)]
        results     (run-cqp-commands corpus (flatten commands) false)]
    (extract-media-info corpus (first results))))


(defmethod transform-results "cwb_speech" [_ queries results]
  (when results
    (let [num-langs (->> queries (map :lang) set count)]
      (map (fn [lines] {:text lines}) (partition num-langs results)))))


(defmethod geo-distr-queries "cwb_speech" [corpus search-id queries metadata-ids]
  (let [named-query (cwb-query-name corpus search-id)
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (construct-query-commands corpus queries metadata-ids named-query search-id
                                               :s-tag "sync_time")
                     (str "save " named-query)
                     "group Last match who_name"]]
    (run-cqp-commands corpus (filter identity (flatten commands)) true)))
