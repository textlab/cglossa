(ns cglossa.db.metadata
  (:require [cglossa.db.shared :refer [sql-query]]))

(def ^:private metadata-pagesize 100)

(defn unconstrained-metadata-values [category-id skip limit]
  (let [total (:total (first (sql-query (str "SELECT out('HasMetadataValue').size() AS total "
                                             "FROM #TARGET")
                                        {:target category-id})))
        res   (sql-query
                (str "SELECT @rid AS id, value AS text FROM "
                     "(SELECT EXPAND(out('HasMetadataValue')) FROM #TARGET "
                     "ORDER BY value SKIP &skip LIMIT &limit)")
                {:target  category-id
                 :strings {:skip skip :limit limit}})]
    [total res]))

(defn constrained-metadata-values [selected-ids category-id skip limit]
  (let [corpus-cat    (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
                          first
                          :corpus_cat)
        initial-value (first (vals selected-ids))
        total         (-> (sql-query (str "SELECT out('DescribesText').in('DescribesText')"
                                          "[corpus_cat = '&category'].size() AS total FROM #TARGET")
                                     {:target  initial-value
                                      :strings {:category corpus-cat}})
                          first
                          :total)
        res           (sql-query
                        (str "SELECT @rid AS id, value AS text FROM "
                             "(SELECT EXPAND(out('DescribesText').in('DescribesText')"
                             "[corpus_cat = '&category']) "
                             "FROM #TARGET ORDER BY value SKIP &skip LIMIT &limit)")
                        {:target  initial-value
                         :strings {:category corpus-cat :skip skip :limit limit}})]
    [total res]))

(defn get-metadata-values [category-id selected-ids page]
  (let [skip  (* (dec page) metadata-pagesize)
        limit (+ skip metadata-pagesize)
        [total res] (if selected-ids
                      (constrained-metadata-values selected-ids category-id skip limit)
                      (unconstrained-metadata-values category-id skip limit))
        more? (> total limit)]
    {:results (map #(select-keys % [:id :text]) res)
     :more?   more?}))
