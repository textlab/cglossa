(ns cglossa.db.metadata
  (:require [cglossa.db.shared :refer [build-sql sql-query]]
            [clojure.string :as str]))

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
  #_(str "SELECT @rid AS id, value AS text FROM MetadataValue "
         "WHERE @rid IN (SELECT intersect($1, $2) "
         "LET $1 = "
         "(SELECT EXPAND(out('DescribesText').in('DescribesText')"
         "[corpus_cat = 'bokmal_translation']) "
         "FROM #14:9438),"
         "$2 = "
         "(SELECT EXPAND(out('DescribesText').in('DescribesText')"
         "[corpus_cat = 'bokmal_translation']) "
         "FROM #14:16810))")
  (let [corpus-cat    (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
                          first
                          :corpus_cat)
        initial-value (first (vals selected-ids))
        fragments     (for [targets (vals selected-ids)]
                        (build-sql (str "(SELECT EXPAND(out('DescribesText').in('DescribesText')"
                                        "[corpus_cat = '&category']) "
                                        "FROM #TARGETS)")
                                   {:targets targets
                                    :strings {:category corpus-cat}}))
        res-sql       (str "SELECT @rid AS id, value AS text FROM MetadataValue WHERE @rid IN "
                           "(SELECT intersect("
                           (str/join ", " (map (fn [i] (str \$ i)) (range (count fragments))))
                           ") LET "
                           (str/join ", " (map-indexed (fn [i frag]
                                                         (str \$ i " = " frag))
                                                       fragments))
                           ") ORDER BY value SKIP &skip LIMIT &limit")
        _ (println res-sql)
        total         (-> (sql-query (str "SELECT out('DescribesText').in('DescribesText')"
                                          "[corpus_cat = '&category'].size() AS total FROM #TARGET")
                                     {:target  initial-value
                                      :strings {:category corpus-cat}})
                          first
                          :total)
        res           (sql-query res-sql {:strings {:skip skip :limit limit}})]
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
