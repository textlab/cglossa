(ns cglossa.search.cwb.written
  "Support for written corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [korma.core :as korma]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [cglossa.search.cwb.shared :refer [cwb-query-name cwb-corpus-name run-cqp-commands
                                               construct-query-commands position-fields
                                               displayed-attrs-command aligned-languages-command
                                               sort-command]]))

(defmethod position-fields :default [_ positions-filename]
  "The database fields that contain corpus positions for texts."
  (korma/raw (str "startpos, endpos INTO OUTFILE '" positions-filename "'")))

(defmethod run-queries :default [corpus search queries metadata-ids step cut sort-key]
  (let [search-id       (:id search)
        named-query     (cwb-query-name corpus search-id)
        commands        [(str "set DataDirectory \"" (fs/tmpdir) \")
                         (cwb-corpus-name corpus queries)
                         (construct-query-commands corpus queries metadata-ids named-query
                                                   search-id cut step)
                         (str "save " named-query)
                         (str "set Context 15 word")
                         "set PrintStructures \"s_id\""
                         "set LD \"{{\""
                         "set RD \"}}\""
                         (displayed-attrs-command corpus queries)
                         (aligned-languages-command corpus queries)
                         ;; Always return the number of results, which may be either total or
                         ;; cut size depending on whether we asked for a cut
                         "size Last"
                         (when (= step 1)
                           ;; When we do the first search, also return the first 100 results,
                           ;; which amounts to two search result pages.
                           "cat Last 0 99")]
        commands        (filter identity (flatten commands))]
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
