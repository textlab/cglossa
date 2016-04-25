(ns cglossa.search.cwb.written
  "Support for written corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [<!!]]
            [me.raynes.fs :as fs]
            [korma.core :as korma :refer [where]]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [cglossa.search.cwb.shared :refer [cwb-query-name cwb-corpus-name run-cqp-commands
                                               construct-query-commands position-fields where-limits
                                               displayed-attrs-command aligned-languages-command
                                               sort-command]]))

(defmethod position-fields :default [_ positions-filename]
  (korma/raw (str "startpos, endpos INTO OUTFILE '" positions-filename "'")))

(defmethod where-limits "cwb" [sql _ startpos endpos]
  (cond-> sql
          true (where (>= :startpos startpos))
          endpos (where (>= :endpos endpos))))

(defmethod run-queries :default [corpus search-id queries metadata-ids step
                                 page-size last-count sort-key]
  (let [step-index (dec step)
        num-ret    (* 2 page-size)      ; number of results to return initially
        parts      (if-let [bounds (get-in corpus [:multicore_bounds step-index])]
                     ;; Multicore bounds have been defined for this corpus. The startpos for the
                     ;; first core in the current step should be one above the last bounds value
                     ;; (i.e., the last endpos) in the previous step.
                     (let [prev-last-bounds (if (= step 1)
                                              -1
                                              (last (get-in corpus
                                                            [:multicore_bounds (dec step-index)])))]
                       (map-indexed (fn [core-index endpos]
                                      (let [startpos (if (zero? core-index)
                                                       ;; If first core, continue where we left off
                                                       ;; in the previous step
                                                       (inc prev-last-bounds)
                                                       ;; Otherwise, contiune from the endpos of
                                                       ;; the previous core
                                                       (inc (nth bounds (dec core-index))))]
                                        [startpos endpos]))
                                    bounds))
                     ;; No multicore bounds defined; in that case, we search the whole
                     ;; corpus in one go in the first step and just return if step != 1.
                     (when (= step 1)
                       [[0 (dec (get-in corpus [:extra-info :size (:code corpus)]))]]))
        scripts    (map-indexed
                     (fn [core [startpos endpos]]
                       (let [named-query (str (cwb-query-name corpus search-id) "_" step "_" core)
                             commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                                          (cwb-corpus-name corpus queries)
                                          (construct-query-commands corpus queries metadata-ids
                                                                    named-query search-id
                                                                    startpos endpos
                                                                    :core-index core)
                                          (str "save " named-query)
                                          (str "set Context 15 word")
                                          "set PrintStructures \"s_id\""
                                          "set LD \"{{\""
                                          "set RD \"}}\""
                                          (displayed-attrs-command corpus queries)
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
                                            (map (run-cqp-commands corpus % true))
                                            (async/to-chan scripts))
        cwb-res    (<!! (async/into [] res-ch))
        hits       (take (- num-ret (or last-count 0)) (apply concat (map first cwb-res)))
        cnt        (reduce + 0 (map #(-> % second Integer/parseInt) cwb-res))]
    [hits cnt]))

(defmethod get-results :default [corpus search queries start end sort-key]
  (let [named-query (cwb-query-name corpus (:id search))
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (str "set Context 15 word")
                     "set PrintStructures \"s_id\""
                     "set LD \"{{\""
                     "set RD \"}}\""
                     (displayed-attrs-command corpus queries)
                     (aligned-languages-command corpus queries)
                     (sort-command named-query sort-key)
                     (str "cat " named-query " " start " " end)]]
    (run-cqp-commands corpus (flatten commands) false)))

(defmethod transform-results :default [_ queries results]
  (when results
    (let [num-langs (->> queries (map :lang) set count)]
      (map (fn [lines] {:text lines}) (partition num-langs results)))))
