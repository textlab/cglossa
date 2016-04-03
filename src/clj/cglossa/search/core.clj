(ns cglossa.search.core
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity table select where insert values]]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.corpus :refer [get-corpus]]
            [clojure.edn :as edn]))

(defentity search)

(defmulti run-queries
  "Multimethod for actually running the received queries in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _ _ _ _ _] (:search_engine corpus)))

(defmulti get-results
  (fn [corpus _ _ _ _ _] (:search_engine corpus)))

(defmulti transform-results
  "Multimethod for transforming search results in a way that is
  appropriate for the search engine of the corpus in question."
  (fn [corpus _ _] (:search_engine corpus)))

(defn- create-search! [corpus-id queries]
  (kdb/with-db core-db
    (insert search (values {:corpus_id corpus-id
                            :user_id   1
                            :queries   (pr-str queries)}))))

(defn- search-by-id [id]
  (kdb/with-db core-db
    (first (select search (where {:id id})))))

(defn search-corpus [corpus-id search-id queries metadata-ids startpos endpos sort-key]
  (let [corpus        (get-corpus {:id corpus-id})
        search-id*    (or search-id (:generated_key (create-search! corpus-id queries)))
        s             (search-by-id search-id*)
        [res cnt] (run-queries corpus s queries metadata-ids startpos endpos sort-key)
        results       (transform-results corpus queries res)
        count         (if (string? cnt) (Integer/parseInt cnt) cnt)]
    {:search  s
     :results results
     :count   count}))

(defn results [corpus-id search-id start end sort-key]
  (let [corpus  (get-corpus {:id corpus-id})
        s       (search-by-id search-id)
        queries (edn/read-string (:queries s))
        [results _] (get-results corpus s queries start end sort-key)]
    (transform-results corpus queries results)))
