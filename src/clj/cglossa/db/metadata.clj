(ns cglossa.db.metadata
  (:require [clojure.set :as set]
            [korma.db :refer [with-db]]
            [korma.core :refer [defentity table belongs-to
                                select fields aggregate where order limit offset]]))

(defentity metadata-category (table :metadata_category))
(defentity metadata-value (table :metadata_value)
                          (belongs-to metadata-category))

;;;; DUMMIES
(defn build-sql [_ _])
(defn sql-query [_ _])

(def ^:private metadata-pagesize 100)

;;;; Note: In the queries below, we vary between following edges (such as HasMetadataValue)
;;;; and using indexes (such as corpus_cat). In each case, the chosen technique was found to
;;;; be the most efficient one when tested on a category with 25,000 values (using OrientDB 2.1.2).

;;;; It turns out that when fetching vertices via an edge (e.g.
;;;; "SELECT flatten(out('HasMetadataValue')) FROM #13:1"), combining ORDER BY and SKIP/LIMIT
;;;; doesn't work, because SKIP/LIMIT is applied *before* ORDER BY, which doesn't make sense
;;;; (since we don't get the desired block from the total set of ordered values, but rather
;;;; the block extracted and only *then* ordered...)

(defn unconstrained-metadata-values [category-id corpus-cat value-filter lim offs]
  (let [conditions (cond-> {:metadata_category_id category-id}
                           value-filter (assoc :text_value ['like (str value-filter \%)]))
        total      (-> (select metadata-value (aggregate (count :*) :total) (where conditions))
                       first
                       :total)
        res        (select metadata-value
                           (fields :id [:text_value :text])
                           (where conditions)
                           (limit lim)
                           (offset offs))]
    [total res]))

(defn constrained-metadata-values [selected-ids category-id corpus-cat value-filter limit offset]
  )
#_(defn constrained-metadata-values [selected-ids category-id corpus-cat value-filter limit offset]
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

(defn get-metadata-categories []
  (select metadata-category (order :name)))

(defn get-metadata-values [category-id value-filter selected-ids page]
  (let [offs  (* (dec page) metadata-pagesize)
        lim   (+ offs metadata-pagesize)
        [total res] (if selected-ids
                      (constrained-metadata-values selected-ids category-id cat
                                                   value-filter lim offs)
                      (unconstrained-metadata-values category-id cat value-filter lim offs))
        more? (> total lim)]
    {:results res
     :more?   more?})
  #_(let [corpus-cat (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
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
