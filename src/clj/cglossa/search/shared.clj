(ns cglossa.search.shared
  (:require [cglossa.db.corpus :refer [get-corpus]]
            [korma.db :as kdb]
            [korma.core :refer [defentity table select fields where insert values]]
            [cglossa.shared :refer [core-db]]))

(defentity corpus)
(defentity search)


(defmulti run-queries
  "Multimethod for actually running the received queries in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _ _ _ _ _ _ _ _ _] (:search_engine corpus)))


(defmulti transform-results
  "Multimethod for transforming search results in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _] (:search_engine corpus)))


(defn create-search! [corpus-code queries]
  (kdb/with-db core-db
    (let [{corpus-id :id} (first (select corpus (fields :id) (where {:code corpus-code})))]
      (insert search (values {:corpus_id corpus-id
                              :user_id   1
                              :queries   (pr-str queries)})))))


(defn search-by-id [id]
  (kdb/with-db core-db
    (first (select search (where {:id id})))))


(defn search-corpus [corpus-code search-id queries metadata-ids step page-size last-count
                     context-size sort-key num-random-hits]
  (let [corpus     (get-corpus {:code corpus-code})
        search-id* (or search-id (:generated_key (create-search! corpus-code queries)))
        [hits cnt cnts] (run-queries corpus search-id* queries metadata-ids step
                                     page-size last-count context-size sort-key
                                     num-random-hits nil)
        results    (transform-results corpus queries hits)
        s          (search-by-id search-id*)]
    {:search     s
     :results    results
     ;; Sum of the number of hits found by the different cpus in this search step
     :count      cnt
     ;; Number of hits found by each cpus in this search step
     :cpu-counts cnts}))
