(ns cglossa.corpora
  (:require [korma.core :refer [select* select subselect fields modifier join where raw]]
            [cglossa.db.metadata :refer [metadata-category metadata-value metadata-value-text
                                         join-selected-values where-selected-values]]
            [korma.core :as korma]
            [clojure.string :as str]))

(defmulti text-selection-info
  "Allows specification of corpus-specific info about the currently selected metadata,
  such as the number of places and countries included in the selection in a spoken corpus."
  (fn [corpus _] (:code corpus)))

(defmethod text-selection-info :default [_ _] nil)


(defmethod text-selection-info "scandiasyn" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `c`.`id`) AS ncountries, COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :c])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :c.id))
                         (join :inner [metadata-value-text :j00] (= :j00.text_id :j0.text_id))
                         (join :inner [metadata-value :p] (= :j00.metadata_value_id :p.id))
                         (where {:c.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "country"}))})
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        ncountries   (:ncountries c)
        place-text   (if (> nplaces 1) "places" "place")
        country-text (if (> ncountries 1) "countries" "country")]
    (str/join " " ["" "from" nplaces place-text "in" ncountries country-text])))

(defmethod text-selection-info "ndc" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `c`.`id`) AS ncountries, COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :c])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :c.id))
                         (join :inner [metadata-value-text :j00] (= :j00.text_id :j0.text_id))
                         (join :inner [metadata-value :p] (= :j00.metadata_value_id :p.id))
                         (where {:c.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "country"}))})
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        ncountries   (:ncountries c)
        place-text   (if (> nplaces 1) "places" "place")
        country-text (if (> ncountries 1) "countries" "country")]
    (str/join " " ["" "from" nplaces place-text "in" ncountries country-text])))


(defmethod text-selection-info "amerikanorsk" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :p])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :p.id))
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        place-text   (if (> nplaces 1) "places" "place")]
    (str/join " " ["" "from" nplaces place-text])))

(defmethod text-selection-info "cans" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :p])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :p.id))
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        place-text   (if (> nplaces 1) "places" "place")]
    (str/join " " ["" "from" nplaces place-text])))


(defmethod text-selection-info "lia_fritt" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :p])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :p.id))
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        place-text   (if (> nplaces 1) "places" "place")]
    (str/join " " ["" "from" nplaces place-text])))

(defmethod text-selection-info "lia" [_ selected-metadata-ids]
  ;; Korma doesn't seem to support any way to express count(distinct...) apart from
  ;; inserting a raw string.
  (let [cnt          (raw "COUNT(DISTINCT `p`.`id`) AS nplaces")
        c            (-> (select* [metadata-value :p])
                         (fields cnt)
                         (join :inner [metadata-value-text :j0] (= :j0.metadata_value_id :p.id))
                         (where {:p.metadata_category_id
                                 (subselect metadata-category
                                            (fields :id)
                                            (where {:code "geo"}))})
                         (join-selected-values selected-metadata-ids)
                         (where-selected-values selected-metadata-ids)
                         select
                         first)
        nplaces      (:nplaces c)
        place-text   (if (> nplaces 1) "places" "place")]
    (str/join " " ["" "from" nplaces place-text])))
