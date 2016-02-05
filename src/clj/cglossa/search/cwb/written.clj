(ns cglossa.search.cwb.written
  "Support for written corpora encoded with the IMS Open Corpus Workbench."
  (:require [me.raynes.fs :as fs]
            [korma.core :as korma]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [cglossa.search.cwb.shared :refer [cwb-query-name cwb-corpus-name run-cqp-commands
                                               construct-query-commands position-fields
                                               sort-command]]
            [clojure.string :as str]))

(defmethod position-fields :default [_ positions-filename]
  "The database fields that contain corpus positions for texts."
  (korma/raw (str "startpos, endpos INTO OUTFILE '" positions-filename "'")))

(defmethod run-queries :default [corpus search queries metadata-ids step cut sort-key]
  (let [search-id   (:id search)
        named-query (cwb-query-name corpus search-id)
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (cwb-corpus-name corpus queries)
                     (construct-query-commands corpus queries metadata-ids named-query
                                               search-id cut step)
                     (when (> step 1)
                       (str "save " named-query))
                     (str "set Context 1 s")
                     "set LD \"{{\""
                     "set RD \"}}\""
                     "show +s_id"
                     (if (= step 1)
                       ;; When we do the first search, which has been cut to the first two pages of
                       ;; search results, we return all those results
                       "cat Last"
                       ;; When we are retrieving more results, we just tell the browser how
                       ;; many results we have found (so far)
                       "size Last")]
        ;;;; TEMPORARY HACK! Change to configure pos attribute based on tagger/corpus
        commands    (if (= "bokmal" (:code corpus))
                      (map #(str/replace % "pos=" "ordkl=") (filter identity (flatten commands)))
                      commands)
        res         (run-cqp-commands corpus (filter identity (flatten commands)))
        results     (when (= step 1) res)
        count       (if (= step 1) (count res) (first res))]
    [results count]))

(defmethod get-results :default [corpus search-id start end sort-key]
  (let [named-query (cwb-query-name corpus search-id)
        commands    [(str "set DataDirectory \"" (fs/tmpdir) \")
                     (str/upper-case (:code corpus))
                     (str "set Context 1 s")
                     "set LD \"{{\""
                     "set RD \"}}\""
                     "show +s_id"
                     (sort-command named-query sort-key)
                     (str "cat " named-query " " start " " end)]]
    (run-cqp-commands corpus (flatten commands))))

(defmethod transform-results :default [_ results]
  (when results
    (map (fn [r] {:text r}) results)))
