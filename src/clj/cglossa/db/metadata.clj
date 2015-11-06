(ns cglossa.db.metadata
  (:require [cglossa.db.shared :refer [build-sql sql-query]]
            [clojure.set :as set]))

(def ^:private metadata-pagesize 100)

;;;; Note: In the queries below, we vary between following edges (such as HasMetadataValue)
;;;; and using indexes (such as corpus_cat). In each case, the chosen technique was found to
;;;; be the most efficient one when tested on a category with 25,000 values (using OrientDB 2.1.2).

;;;; It turns out that when fetching vertices via an edge (e.g.
;;;; "SELECT flatten(out('HasMetadataValue')) FROM #13:1"), combining ORDER BY and SKIP/LIMIT
;;;; doesn't work, because SKIP/LIMIT is applied *before* ORDER BY, which doesn't make sense
;;;; (since we don't get the desired block from the total set of ordered values, but rather
;;;; the block extracted and only *then* ordered...)

(defn unconstrained-metadata-values [category-id corpus-cat value-filter skip limit]
  (let [total (-> (if value-filter
                    (sql-query (str "SELECT COUNT(*) AS total FROM "
                                    "(SELECT flatten(out('HasMetadataValue')) from #TARGET) "
                                    "WHERE value LIKE '&filter%'")
                               {:target  category-id
                                :strings {:filter value-filter}})
                    (sql-query (str "SELECT out('HasMetadataValue').size() AS total "
                                    "FROM #TARGET")
                               {:target category-id}))
                  first
                  :total)
        res   (if value-filter
                (sql-query (str "SELECT @rid AS id, value AS text FROM MetadataValue "
                                "WHERE corpus_cat = ? AND value LIKE ? "
                                "ORDER BY text SKIP &skip LIMIT &limit")
                           {:target     category-id
                            :sql-params [corpus-cat (str value-filter "%")]
                            :strings    {:skip skip :limit limit}})
                (sql-query (str "SELECT @rid AS id, value AS text FROM "
                                "(SELECT flatten(out('HasMetadataValue')) FROM #TARGET "
                                "ORDER BY value SKIP &skip LIMIT &limit)")
                           {:target  category-id
                            :strings {:skip skip :limit limit}}))]
    [total res]))

(defn constrained-metadata-values [selected-ids category-id corpus-cat value-filter skip limit]
  (let [cat-results     (;; Iterate over all categories where one or more values have been selected
                          for [targets (vals selected-ids)]
                          ;; For the set of values that have been selected in this category,
                          ;; first find all the texts they are associated with, and then
                          ;; all the values in the corpus-cat category that those texts are
                          ;; associated with in turn. In other words, we OR (take the union of)
                          ;; all values that match one or more selections within a single category.
                          (->> (sql-query (str "SELECT out('DescribesText').in('DescribesText')"
                                               "[corpus_cat = '&category'] AS vals FROM #TARGETS)")
                                          {:targets targets
                                           :strings {:category corpus-cat}})
                               (map :vals)
                               flatten))
        ;; Get the intersection of the sets of rids from each category. This gives us an AND
        ;; relationship between selections made in different categories.
        intersected-ids (apply set/intersection (map set cat-results))
        total           (count intersected-ids)
        res             (sql-query (str "SELECT @rid AS id, value AS text FROM #TARGETS "
                                        "ORDER BY text SKIP &skip LIMIT &limit")
                                   {:targets intersected-ids
                                    :strings {:skip skip :limit limit}})]
    [total res]))

(defn get-metadata-values [category-id value-filter selected-ids page]
  (let [corpus-cat (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
                       first
                       :corpus_cat)
        skip       (* (dec page) metadata-pagesize)
        limit      (+ skip metadata-pagesize)
        [total res] (if selected-ids
                      (constrained-metadata-values selected-ids category-id corpus-cat
                                                   value-filter skip limit)
                      (unconstrained-metadata-values category-id corpus-cat
                                                     value-filter skip limit))
        more?      (> total limit)]
    {:results (map #(select-keys % [:id :text]) res)
     :more?   more?}))
