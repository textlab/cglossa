(ns cglossa.db.metadata
  (:require [korma.db :refer [with-db]]
            [korma.core :refer [defentity table belongs-to select select* fields modifier aggregate
                                where join order limit offset sqlfn]]))

(defentity metadata-category (table :metadata_category))
(defentity metadata-value (table :metadata_value)
                          (belongs-to metadata-category))
(defentity metadata-value-text (table :metadata_value_text))

(def ^:private metadata-pagesize 100)

(defn- unconstrained-metadata-values [category-id value-filter lim offs]
  (let [conditions (cond-> {:metadata_category_id category-id}
                           value-filter (assoc :text_value ['like (str value-filter \%)]))
        res        (select metadata-value
                           (fields :id [:text_value :text])
                           (modifier "SQL_CALC_FOUND_ROWS")
                           (where conditions)
                           (limit lim)
                           (offset offs))
        ;; NOTE: MySQL specific way to get the number of rows that would have been fetched
        ;; by the previous query if it did not have a limit clause
        total      (-> (korma.core/exec-raw "SELECT FOUND_ROWS() AS total" :results) first :total)]
    [total res]))

(defn- join-selected-values [query selected-ids]
  "Adds a join with the metadata_value_text table for each metadata category
  for which we have already selected one or more values."
  (reduce (fn [q join-index]
            (let [alias1         (str \j (inc join-index))
                  alias2         (str \j join-index)
                  make-fieldname #(keyword (str % ".text_id"))]
              (join q :inner [metadata-value-text alias1]
                    (= (make-fieldname alias1) (make-fieldname alias2)))))
          query
          (-> selected-ids count range)))

(defn- where-selected-values [query selected-ids]
  "For each metadata category for which we have already selected one or more
  values, adds a 'where' clause with the ids of the metadata values in that
  category. The 'where' clause is associated with the corresponding instance of
  the metadata_value_text table that is joined in by the join-selected-values
  function.

  This gives us an OR (union) relationship between values from the same
  category and an AND (intersection) relationship between values from different
  categories."
  (let [cats (map-indexed (fn [index [_ ids]] [index ids]) selected-ids)]
    (reduce (fn [q [cat-index cat-ids]]
              (let [alias     (str \j (inc cat-index))
                    fieldname (keyword (str alias ".metadata_value_id"))]
                (where q {fieldname [in cat-ids]})))
            query
            cats)))

(defn filter-value [query value-filter]
  (if value-filter
    (where query {:metadata_value.text_value [like (str value-filter \%)]})
    query))

(defn- constrained-metadata-values [selected-ids category-id value-filter lim offs]
  (let [res   (-> (select* metadata-value)
                  (fields :id [:text_value :text])
                  (modifier "SQL_CALC_FOUND_ROWS DISTINCT")
                  (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :id))
                  (join-selected-values selected-ids)
                  (where-selected-values selected-ids)
                  (where {:metadata_category_id category-id})
                  (filter-value value-filter)
                  (limit lim)
                  (offset offs)
                  (select))
        ;; NOTE: MySQL specific way to get the number of rows that would have been fetched
        ;; by the previous query if it did not have a limit clause
        total (-> (korma.core/exec-raw "SELECT FOUND_ROWS() AS total" :results) first :total)]
    [total res]))

(defn get-metadata-categories []
  (select metadata-category (order :name)))

(defn get-metadata-values [category-id value-filter selected-ids page]
  (let [offs  (* (dec page) metadata-pagesize)
        lim   (+ offs metadata-pagesize)
        [total res] (if selected-ids
                      (constrained-metadata-values selected-ids category-id value-filter lim offs)
                      (unconstrained-metadata-values category-id value-filter lim offs))
        more? (> total lim)]
    {:results res
     :more?   more?}))
