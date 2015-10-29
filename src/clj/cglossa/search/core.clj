(ns cglossa.search.core
  (:require [cglossa.db.shared :as db]))

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

(defn- create-search [corpus queries]
  (let [search (db/vertex->map (db/run-sql "create vertex Search set queries = ?" [(str queries)]))]
    (db/run-sql (str "create edge InCorpus from " (:rid search) " to " (:rid corpus)))
    search))

(defn search [corpus-id search-id queries metadata-ids step cut sort-by]
  (let [corpus           (first (db/sql-query (str "select @rid, code, search_engine, encoding "
                                                   "from #TARGET") {:target corpus-id}))
        search           (if (= step 1)
                           (create-search corpus queries)
                           (first (db/sql-query "select from #TARGET" {:target search-id})))
        results-or-count (run-queries corpus search queries metadata-ids step cut sort-by)
        result           (if (= step 1)
                           ;; On the first search, we get actual search results back
                           (transform-results corpus results-or-count)
                           ;; On subsequent searches, which just retrieve more results from
                           ;; the same query, we just get the number of results found (so far)
                           (Integer/parseInt (first results-or-count)))]
    {:search (dissoc search :class)
     :result result}))

(defn results [search-id start end sort-by]
  (let [corpus (first (db/sql-query "select expand(out('InCorpus')) from #TARGET"
                                    {:target search-id}))
        results (get-results corpus search-id start end sort-by)]
    (transform-results corpus results)))
