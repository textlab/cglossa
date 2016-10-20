(ns cglossa.search.cwb.written
  "Support for written corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [<!!]]
            [me.raynes.fs :as fs]
            [korma.core :as korma :refer [where order]]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [cglossa.search.cwb.shared :refer [cwb-query-name cwb-corpus-name run-cqp-commands
                                               construct-query-commands position-fields
                                               order-position-fields where-limits
                                               displayed-attrs-command aligned-languages-command
                                               sort-command]]))

(defmethod position-fields :default [_ positions-filename]
  (korma/raw (str "startpos, endpos INTO OUTFILE '" positions-filename "'")))

(defmethod order-position-fields :default [sql corpus]
  (order sql :startpos))

(defmethod where-limits "cwb" [sql _ startpos endpos]
  (cond-> sql
          true (where (>= :startpos startpos))
          endpos (where (>= :endpos endpos))))

(defmethod run-queries :default [corpus search-id queries metadata-ids step
                                 page-size last-count context-size sort-key]
  (let [step-index (dec step)
        num-ret    (* 2 page-size)      ; number of results to return initially
        parts      (if-let [bounds (get-in corpus [:multicpu_bounds step-index])]
                     ;; Multicpu bounds have been defined for this corpus. The startpos for the
                     ;; first cpu in the current step should be one above the last bounds value
                     ;; (i.e., the last endpos) in the previous step.
                     (let [prev-last-bounds (if (= step 1)
                                              -1
                                              (last (get-in corpus
                                                            [:multicpu_bounds (dec step-index)])))]
                       (map-indexed (fn [cpu-index endpos]
                                      (let [startpos (if (zero? cpu-index)
                                                       ;; If first cpu, continue where we left off
                                                       ;; in the previous step
                                                       (inc prev-last-bounds)
                                                       ;; Otherwise, continue from the endpos of
                                                       ;; the previous cpu
                                                       (inc (nth bounds (dec cpu-index))))]
                                        [startpos endpos]))
                                    bounds))
                     ;; No multicpu bounds defined; in that case, we search the whole
                     ;; corpus in one go in the first step and just return if step != 1.
                     (when (= step 1)
                       [[0
                         (dec (get-in corpus [:extra-info :size
                                              (str/lower-case (cwb-corpus-name corpus queries))]))]]))
        scripts    (map-indexed
                     (fn [cpu [startpos endpos]]
                       (let [named-query (str (cwb-query-name corpus search-id) "_" step "_" cpu)
                             commands    [(str "set DataDirectory \"" (fs/tmpdir) "/glossa\"")
                                          (cwb-corpus-name corpus queries)
                                          (construct-query-commands corpus queries metadata-ids
                                                                    named-query search-id
                                                                    startpos endpos
                                                                    :cpu-index cpu)
                                          (str "save " named-query)
                                          (str "set Context " context-size " word")
                                          "set PrintStructures \"s_id\""
                                          "set LD \"{{\""
                                          "set RD \"}}\""
                                          (displayed-attrs-command corpus queries nil)
                                          (aligned-languages-command corpus queries)
                                          ;; Always return the number of results, which may be
                                          ;; either total or cut size depending on whether we
                                          ;; restricted the corpus positions
                                          "size Last"
                                          ;; No last-count means this is the first request of this
                                          ;; search, in which case we return the first two pages of
                                          ;; search results (or as many as we found in this first
                                          ;; part of the corpus). If we got a last-count value,
                                          ;; it means this is not the first request of this search.
                                          ;; In that case, we check to see if the previous request(s)
                                          ;; managed to fill those two pages, and if not we return
                                          ;; results in order to keep filling them.
                                          (when (or (nil? last-count)
                                                    (< last-count num-ret))
                                            (str "cat Last 0 " (dec num-ret)))]]
                         (filter identity (flatten commands))))
                     parts)
        res-ch     (async/chan)
        _          (async/pipeline-blocking (count scripts)
                                            res-ch
                                            (map #(run-cqp-commands corpus % true))
                                            (async/to-chan scripts))
        cwb-res    (<!! (async/into [] res-ch))
        hits       (take (- num-ret (or last-count 0)) (apply concat (map first cwb-res)))
        ;; Number of hits found by each cpu in this search step
        cnts       (mapv #(-> % second Integer/parseInt) cwb-res)
        ;; Sum of the number of hits found by the different cpus in this search step
        cnt        (+ (or last-count 0)
                      (reduce + 0 cnts))]
    [hits cnt cnts]))

;; For written CWB corpora that don't use multicore processing (e.g. multilingual corpora)
(defmethod get-results ["cwb" nil] [corpus search queries start end _ context-size sort-key attrs]
  (let [named-query (str (cwb-query-name corpus (:id search)) "_1_0")
        commands    [(str "set DataDirectory \"" (fs/tmpdir) "/glossa\"")
                     (cwb-corpus-name corpus queries)
                     (str "set Context " context-size " word")
                     "set PrintStructures \"s_id\""
                     "set LD \"{{\""
                     "set RD \"}}\""
                     (displayed-attrs-command corpus queries attrs)
                     (aligned-languages-command corpus queries)
                     (sort-command named-query sort-key)
                     (str "cat " named-query (when (and start end)
                                               (str " " start " " end)))]]
    (run-cqp-commands corpus (flatten commands) false)))

(defmethod get-results :default [corpus search queries start end cpu-counts
                                 context-size sort-key attrs]
  (let [named-query   (cwb-query-name corpus (:id search))
        nres-1        (when (and start end)
                        (- end start))
        [first-file first-start] (reduce-kv
                                   (fn [sum k v]
                                     (let [new-sum (+ sum v)]
                                       (if (> new-sum start)
                                         ;; We found the first result file to fetch results from.
                                         ;; Return the index of that file as well as the first
                                         ;; result index to fetch from this file; e.g. if we want
                                         ;; to fetch results starting at index 100 and the sum of
                                         ;; counts up to this file is 93, we should start fetching
                                         ;; from index 7 in this file.
                                         (reduced [k (- start sum)])
                                         new-sum)))
                                   0
                                   cpu-counts)
        last-file     (loop [sum        0
                             counts     cpu-counts
                             file-index 0]
                        (let [new-sum (+ sum (first counts))]
                          ;; If either the end index can be found in the current file (meaning
                          ;; that if we add the current count to the sum, we exceed the end
                          ;; index) or there are no more files, the current file should be the
                          ;; last one we fetch results from.
                          (if (or (and end (> new-sum end))
                                  (nil? (next counts)))
                            file-index
                            ;; Otherwise, continue with the next file
                            (recur new-sum
                                   (next counts)
                                   (inc file-index)))))
        ;; Generate the names of the files containing saved CQP queries
        files         (vec (flatten (map-indexed
                                      (fn [i cpu-bounds]
                                        (mapv #(str named-query "_" (inc i) "_" %)
                                              (range (count cpu-bounds))))
                                      (:multicpu_bounds corpus))))
        ;; Select the range of files that contains the range of results we are asking for
        ;; and remove files that don't actually contain any results
        nonzero-files (filter identity (map (fn [file count]
                                              (when-not (zero? count)
                                                file))
                                            (subvec files first-file (inc last-file))
                                            (subvec cpu-counts first-file (inc last-file))))
        ;; For the first result file, we need to adjust the start and end index according to
        ;; the number of hits that were found in previous files. For the remaining files, we
        ;; set the start index to 0, and we might as well set the end index to [number of
        ;; desired results minus one], since CQP won't actually mind if we ask for results
        ;; beyond those available. If no start and end positions were given, we will return
        ;; all hits (typically used for exporting results to file etc.)
        indexes       (if (and start end)
                        (cons [first-start (+ first-start nres-1)]
                              (repeat [0 nres-1]))
                        (repeat [nil nil]))
        scripts       (map
                        (fn [result-file [start end]]
                          (let [commands [(str "set DataDirectory \"" (fs/tmpdir) "/glossa\"")
                                          (cwb-corpus-name corpus queries)
                                          (str "set Context " context-size " word")
                                          "set PrintStructures \"s_id\""
                                          "set LD \"{{\""
                                          "set RD \"}}\""
                                          (displayed-attrs-command corpus queries attrs)
                                          (aligned-languages-command corpus queries)
                                          (sort-command named-query sort-key)
                                          (str "cat " result-file (when (and start end)
                                                                    (str " " start " " end)))]]
                            (filter identity (flatten commands))))
                        nonzero-files
                        indexes)
        cwb-res       (map #(run-cqp-commands corpus % false) scripts)
        all-res       (apply concat (map first cwb-res))
        ;; Since we asked for 'end' number of results even from the last file, we may have got
        ;; more than we asked for (when adding up all results from all files), so make sure we
        ;; only return the desired number of results if it was specified.
        res           (if nres-1
                        (take (inc nres-1) all-res)
                        all-res)]
    [res nil]))

(defmethod transform-results :default [_ queries results]
  (when results
    (let [queried-langs (->> queries (map :lang) set)
          num-langs     (if (some #{"org" "korr"} queried-langs)
                          2
                          (count queried-langs))]
      (map (fn [lines] {:text lines}) (partition num-langs results)))))
