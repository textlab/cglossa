(ns cglossa.search.core
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity table select where insert values]]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.corpus :refer [corpus-by-id]]))

;;;; DUMMIES
(defn run-sql
  ([_ _])
  ([_]))
(defn sql-query [_ _])
(defn vertex->map [_])

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

(defn- get-search [id]
  (kdb/with-db core-db
    (first (select search (where {:id id})))))

(defn search-corpus [corpus-id search-id queries metadata-ids step cut sort-by]
  (let [corpus           (corpus-by-id corpus-id)
        search-id*       (if (= step 1)
                           (:generated_key (create-search! corpus-id queries))
                           search-id)
        s                (get-search search-id*)
        results-or-count (run-queries corpus s queries metadata-ids step cut sort-by)
        result           (if (= step 1)
                           ;; On the first search, we get actual search results back
                           (transform-results corpus results-or-count)
                           ;; On subsequent searches, which just retrieve more results from
                           ;; the same query, we just get the number of results found (so far)
                           (Integer/parseInt (first results-or-count)))]
    {:search s
     :result result}))

(defn results [search-id start end sort-by]
  (let [corpus (first (sql-query "select expand(out('InCorpus')) from #TARGET"
                                    {:target search-id}))
        results (get-results corpus search-id start end sort-by)]
    (transform-results corpus results)))
