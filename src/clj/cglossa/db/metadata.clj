(ns cglossa.db.metadata
  (:require [korma.core :refer [defentity table belongs-to select select* fields modifier
                                subselect where join order limit offset sqlfn raw]]
            [korma.db :as kdb]
            [cglossa.shared :refer [corpus-connections]]))

(defentity metadata-category (table :metadata_category))
(defentity metadata-value (table :metadata_value)
                          (belongs-to metadata-category))
(defentity metadata-value-text (table :metadata_value_text))

(def ^:private metadata-pagesize 100)
(def ^:private show-texts-pagesize 100)

(defn- unconstrained-metadata-values [category-id value-filter lim offs]
  (let [conditions (cond-> {:metadata_category_id category-id}
                           value-filter (assoc :text_value ['like (str value-filter \%)]))
        res        (select metadata-value
                           (fields :id [:text_value :text])
                           (modifier "SQL_CALC_FOUND_ROWS")
                           (where conditions)
                           (order :text_value)
                           (limit lim)
                           (offset offs))
        total      (-> (korma.core/exec-raw "SELECT FOUND_ROWS() AS total" :results) first :total)]
    [total res]))

(defn join-selected-values
  "Adds a join with the metadata_value_text table for each metadata category
  for which we have already selected one or more values."
  [sql selected-ids]
  (reduce (fn [q join-index]
            (let [alias1         (str \j (inc join-index))
                  alias2         (str \j join-index)
                  make-fieldname #(keyword (str % ".text_id"))]
              (join q :inner [metadata-value-text alias1]
                    (= (make-fieldname alias1) (make-fieldname alias2)))))
          sql
          (-> selected-ids count range)))

(defn where-selected-values
  "For each metadata category for which we have already selected one or more
  values, adds a 'where' clause with the ids of the metadata values in that
  category. The 'where' clause is associated with the corresponding instance of
  the metadata_value_text table that is joined in by the join-selected-values
  function.

  This gives us an OR (union) relationship between values from the same
  category and an AND (intersection) relationship between values from different
  categories."
  [sql selected-ids]
  (let [cats (map-indexed (fn [index [_ ids]] [index ids]) selected-ids)]
    (reduce (fn [q [cat-index cat-ids]]
              (let [alias     (str \j (inc cat-index))
                    fieldname (keyword (str alias ".metadata_value_id"))]
                (where q {fieldname [in cat-ids]})))
            sql
            cats)))

(defn filter-value [sql value-filter]
  (if value-filter
    (where sql {:metadata_value.text_value [like (str value-filter \%)]})
    sql))

(defn- constrained-metadata-values [selected-ids category-id value-filter lim offs]
  (let [res   (-> (select* metadata-value)
                  (fields :id [:text_value :text])
                  (modifier "SQL_CALC_FOUND_ROWS DISTINCT")
                  (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :id))
                  (join-selected-values selected-ids)
                  (where-selected-values selected-ids)
                  (where {:metadata_category_id category-id})
                  (filter-value value-filter)
                  (order :text_value)
                  (limit lim)
                  (offset offs)
                  (select))
        total (-> (korma.core/exec-raw "SELECT FOUND_ROWS() AS total" :results) first :total)]
    [total res]))

(defn get-metadata-categories []
  (select metadata-category))

(defn get-metadata-values [category-id value-filter selected-ids page]
  (let [offs  (* (dec page) metadata-pagesize)
        lim   metadata-pagesize
        [total res] (if selected-ids
                      (constrained-metadata-values selected-ids category-id value-filter lim offs)
                      (unconstrained-metadata-values category-id value-filter lim offs))
        more? (> total lim)]
    {:results res
     :more?   more?}))

(defn show-texts [selected-metadata page sort-column-id sort-ascending?]
  (let [mdata     (remove (fn [[_ ids]] (nil? ids)) selected-metadata)
        offs      (* (dec page) show-texts-pagesize)
        res       (-> (select* metadata-value)
                      (fields :j0.text_id :metadata_category_id :text_value)
                      (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :id))
                      (join-selected-values mdata)
                      (where-selected-values mdata)
                      (order :j0.text_id)
                      (order :metadata_category_id)
                      (select))
        rows      (->> res
                       (partition-by :text_id)
                       (map (fn [text]
                              (into {} (map (fn [value]
                                              [(:metadata_category_id value) (:text_value value)])
                                            text))))
                       (sort-by #(get % (or sort-column-id 1))
                                (if sort-ascending? compare #(compare %2 %1))))
        total     (count rows)
        max-pages (-> (/ total show-texts-pagesize) Math/ceil int)]
    {:rows      (->> rows (drop offs) (take show-texts-pagesize))
     :max-pages max-pages}))

(defn num-selected-texts [selected-metadata-ids]
  ;; If we don't get any selected metadata, just return nil, which means that all text
  ;; were selected.
  (when (seq selected-metadata-ids)
    ;; Korma doesn't seem to support any way to express count(distinct...) apart from
    ;; inserting a raw string.
    (let [cnt      (raw "COUNT(DISTINCT `text_value`) AS cnt")
          tid-code (if (empty? (-> (select metadata-category
                                           (where {:code "hd_tid_hd"}))))
                     "tid"
                     "hd_tid_hd")]
      (-> (select* [metadata-value])
          (fields cnt)
          (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :id))
          (join-selected-values selected-metadata-ids)
          (where-selected-values selected-metadata-ids)
          (where {:metadata_category_id
                  (subselect metadata-category (fields :id) (where {:code tid-code}))})
          (select)
          first
          :cnt))))

(defn result-metadata [corpus-code text-id]
  (let [tid-code (if (empty? (-> (select metadata-category
                                         (where {:code "hd_tid_hd"}))))
                   "tid"
                   "hd_tid_hd")]
    (kdb/with-db (get @corpus-connections corpus-code)
      (-> (select* [metadata-value :v1])
          (modifier "DISTINCT")
          (fields :v1.metadata_category_id :v1.text_value)
          (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :v1.id))
          (join :inner [metadata-value-text :j1] (= :j1.text_id :j0.text_id))
          (join :inner [metadata-value :v2] (= :j1.metadata_value_id :v2.id))
          (join :inner [metadata-category :c] (= :v2.metadata_category_id :c.id))
          (where {:c.code        tid-code
                  :v2.text_value text-id})
          (select)))))
