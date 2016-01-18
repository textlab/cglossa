(ns cglossa.search.core
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity table select where insert values]]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.corpus :refer [corpus-by-id]]))

(defentity search)

(defmulti run-queries
  "Multimethod for actually running the received queries in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _ _ _ _ _] (:search_engine corpus)))

(defmulti get-results
  (fn [corpus _ _ _ _] (:search_engine corpus)))

(defmulti transform-results
  "Multimethod for transforming search results in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _] (:search_engine corpus)))

(defn- create-search! [corpus-id queries]
  (kdb/with-db core-db
    (insert search (values {:corpus_id corpus-id
                            :user_id   1
                            :queries   (pr-str queries)}))))

(defn- search-by-id [id]
  (kdb/with-db core-db
    (first (select search (where {:id id})))))

(defn search-corpus [corpus-id search-id queries metadata-ids step cut sort-key]
  (let [corpus     (corpus-by-id corpus-id)
        search-id* (if (= step 1)
                     (:generated_key (create-search! corpus-id queries))
                     search-id)
        s          (search-by-id search-id*)
        [res cnt]  (run-queries corpus s queries metadata-ids step cut sort-key)
        results    (transform-results corpus res)
        count      (if (string? cnt) (Integer/parseInt cnt) cnt)]
    {:search  s
     :results results
     :count   count}))

(defn results [corpus-id search-id start end sort-key]
  (let [corpus  (corpus-by-id corpus-id)
        results (get-results corpus search-id start end sort-key)]
    (transform-results corpus results)))
