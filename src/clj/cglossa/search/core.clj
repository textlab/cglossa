(ns cglossa.search.core
  (:require [clojure.string :as str]
            [korma.db :as kdb]
            [korma.core :refer [defentity table select fields where insert values]]
            [clojure.edn :as edn]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.corpus :refer [get-corpus]]
            [cglossa.search.shared :refer [corpus search create-search! search-corpus
                                           run-queries transform-results search-by-id]]
            [cglossa.search.download :as download]))

(defmulti get-results
  (fn [corpus _ _ _ _ _ _ _ _] [(:search_engine corpus) (seq (:multicpu_bounds corpus))]))

(defmulti geo-distr-queries
  "Multimethod for running a query and returning geographical distribution of
   results."
  (fn [corpus _ _] (:search_engine corpus)))

(defn stats-corpus [corpus-code search-id queries metadata-ids step page-size last-count
                    context-size sort-key freq-attr]
  (let [corpus     (get-corpus {:code corpus-code})
        search-id* (or search-id (:generated_key (create-search! corpus-code queries)))
        [hits cnt cnts] (run-queries corpus search-id* queries metadata-ids 1
                                     1000000 last-count context-size sort-key nil
                                     (str "tabulate Last "
                                          (str/join ", " (map #(str "match .. matchend " (name %)) freq-attr))
                                          " >\"|LC_ALL=C awk '{f[$0]++}END{for(k in f){print f[k], k}}' |LC_ALL=C sort -nr\""))
        s          (search-by-id search-id*)]
    {:search     s
     :results    hits
     ;; Sum of the number of hits found by the different cpus in this search step
     :count      cnt
     ;; Number of hits found by each cpus in this search step
     :cpu-counts cnts}))

(defn results [corpus-code search-id start end cpu-counts context-size sort-key]
  (let [corpus      (get-corpus {:code corpus-code})
        s           (search-by-id search-id)
        queries     (edn/read-string (:queries s))
        start*      (Integer/parseInt start)
        end*        (Integer/parseInt end)
        cpu-counts* (edn/read-string cpu-counts)
        [results _] (get-results corpus s queries start* end* cpu-counts* context-size sort-key nil)]
    (transform-results corpus queries results)))


(defn geo-distr [corpus-code search-id metadata-ids]
  (let [corpus  (get-corpus {:code corpus-code})
        results (geo-distr-queries corpus search-id metadata-ids)
        s       (search-by-id search-id)]
    {:search  s
     :results results}))

(defn download-results [corpus-code search-id cpu-counts format headers? attrs context-size]
  (let [corpus   (get-corpus {:code corpus-code})
        s        (search-by-id search-id)
        queries  (edn/read-string (:queries s))
        start    0
        end      (when (= format "excel") 49999)
        sort-key "position"
        [results _] (get-results corpus s queries start end cpu-counts context-size sort-key attrs)
        rows     (for [line results]
                   ;; Extract corpus position, sentence/utterance ID, left context, match and right
                   ;; context from the result line
                   (or (next (re-find #"^\s*(\d+):\s*<.+?\s(.+?)>:\s*(.+?)\s*\{\{(.+?)\}\}\s+(.+)"
                                      line))
                       [nil nil line nil nil]))]
    (case format
      "excel" (download/excel-file search-id headers? rows)
      "tsv" (download/csv-file :tsv search-id headers? rows)
      "csv" (download/csv-file :csv search-id headers? rows))))
