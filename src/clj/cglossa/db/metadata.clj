(ns cglossa.db.metadata
  (:require [cglossa.db.shared :refer [build-sql sql-query]]
            [clojure.set :as set]))

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
  (let [corpus-cat      (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
                            first
                            :corpus_cat)
        initial-value   (first (vals selected-ids))
        cat-results     (;; Iterate over all categories where one or more values have been selected
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
                               first
                               :vals))
        ;; Get a seq of seqs, with each seq containing the rids we found for a particular category
        value-ids       (map #(if (sequential? %) % [%]) cat-results)
        ;; Get the intersection of all those seqs. This gives us an AND relationship between
        ;; selections made in different categories.
        intersected-ids (apply set/intersection (map set value-ids))
        total           (count intersected-ids)
        res             (sql-query (str "SELECT @rid AS id, value AS text FROM #TARGETS "
                                        "ORDER BY text SKIP &skip LIMIT &limit")
                                   {:targets intersected-ids
                                    :strings {:skip skip :limit limit}})]
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
