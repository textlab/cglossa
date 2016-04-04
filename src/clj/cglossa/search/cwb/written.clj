(ns cglossa.search.cwb.written
  "Support for written corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
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

(defmethod run-queries :default [corpus search queries metadata-ids startpos endpos
                                 page-size last-count sort-key]
  (let [search-id   (:id search)
        named-query (cwb-query-name corpus search-id)
        ret-results (* 2 page-size)     ; number of results to return initially
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (construct-query-commands corpus queries metadata-ids named-query
                                               search-id startpos endpos)
                     (str "save " named-query)
                     (str "set Context 15 word")
                     "set PrintStructures \"s_id\""
                     "set LD \"{{\""
                     "set RD \"}}\""
                     (displayed-attrs-command corpus queries)
                     (aligned-languages-command corpus queries)
                     ;; Always return the number of results, which may be either total or
                     ;; cut size depending on whether we restricted the corpus positions
                     "size Last"
                     (cond
                       ;; No last-count means this is the first request of this search, in which
                       ;; case we return the first two pages of search results (or as many as
                       ;; we found in this first part of the corpus)
                       (nil? last-count) (str "cat Last 0 " (dec ret-results))
                       ;; If we got a last-count value, it means this is not the first request
                       ;; of this search. In that case, we only return enough results to
                       ;; fill up the first two pages of search results if they could not already
                       ;; be filled by previous requests.
                       (< last-count ret-results) (str "cat Last " last-count " "
                                                       (dec (- ret-results last-count)))
                       :else nil)]]
    (run-cqp-commands corpus (filter identity (flatten commands)) true)))

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
