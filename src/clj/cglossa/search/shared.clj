(ns cglossa.search.shared
  (:require [clojure.string :as str]
            [cglossa.db.corpus :refer [get-corpus]]
            [me.raynes.conch.low-level :as sh]
            [korma.db :as kdb]
            [korma.core :refer [defentity table select fields where insert values]]
            [cglossa.shared :refer [core-db]]))

(defentity corpus)
(defentity search)


(defmulti run-queries
  "Multimethod for actually running the received queries in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _ _ _ _ _ _ _ _ _ _] (:search_engine corpus)))


(defmulti transform-results
  "Multimethod for transforming search results in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _] (:search_engine corpus)))


(defn create-search! [corpus-code queries metadata-ids]
  (kdb/with-db core-db
    (let [{corpus-id :id} (first (select corpus (fields :id) (where {:code corpus-code})))]
      (insert search (values {:corpus_id corpus-id
                              :user_id   1
                              :queries   (pr-str queries)
                              :metadata_value_ids (str metadata-ids)})))))


(defn search-by-id [id]
  (kdb/with-db core-db
    (first (select search (where {:id id})))))

;; If the number of running CQP processes exceeds this number, we do not allow a new
;; search in a corpus that does parallel search using all cpus to be started.
(def max-cqp-processes 16)

(defn search-corpus [corpus-code search-id queries metadata-ids step page-size last-count
                     context-size sort-key num-random-hits random-hits-seed]
  (let [cqp-procs  (sh/proc "pgrep" "cqp")
        out        (sh/stream-to-string cqp-procs :out)
        ncqp-procs (-> out str/split-lines count)
        corpus     (get-corpus {:code corpus-code})]
    ;; If we are searching a corpus that does parallel search with multiple cpus and
    ;; this is the first search step, we check that we don't already exceed the max number
    ;; of CQP processes before starting the search. If we are at step 2 or 3, we should finish
    ;; what we started. Corpora that don't use multiple cpus are assumed to be small and
    ;; should cause problems even with a lot of CQP processes.
    (if (or (nil? (:multicpu_bounds corpus)) (< ncqp-procs max-cqp-processes) (> step 1))
      (let [search-id* (or search-id (:generated_key (create-search! corpus-code queries metadata-ids)))
            [hits cnt cnts] (run-queries corpus search-id* queries metadata-ids step
                                         page-size last-count context-size sort-key
                                         num-random-hits random-hits-seed nil)
            results    (transform-results corpus queries hits)
            s          (search-by-id search-id*)]
        {:search     s
         :results    results
         ;; Sum of the number of hits found by the different cpus in this search step
         :count      cnt
         ;; Number of hits found by each cpus in this search step
         :cpu-counts cnts})
      (do
        (println "TOO MANY CQP PROCESSES: " ncqp-procs "; aborting search at " (str (java.time.LocalDateTime/now)))
        {:results "The server is busy. Please try again."}))))
