(ns cglossa.data-import.metadata-values
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [clojure.data.avl :as avl]
            [cglossa.data-import.utils :as utils]))

(defn- create-value-data [unique-vals]
  "Creates seqs of [category-id, value] vectors from each column and
  concatenate them; this will be imported into the metadata_values table."
  (->> unique-vals
       (map-indexed (fn [index col-unique-vals]
                      ;; Return the metadata category id and the metadata value. The metadata
                      ;; category table is always truncated before import, so we know that the
                      ;; category ids (which is equal to the value column number) start at 1.
                      (map (fn [val] [(inc index) val]) col-unique-vals)))
       (apply concat)))

(defn- create-value-text-joins [value-cols unique-vals]
  "Creates data to import into the metadata_values_texts join table, linking
  metadata values and texts in a many-to-many relationship."
  (let [prev-col-counts (atom 0)]
    (mapcat (fn [col-vals col-unique-vals]
              (let [prev-col-counts* @prev-col-counts]
                (swap! prev-col-counts + (count col-unique-vals))
                (map-indexed (fn [row-index val]
                               ;; Since each row in the input tsv file represents a corpus text, and
                               ;; the texts table is truncated before import, we can use
                               ;; (inc row-index) as text id. The metadata value is calculated as
                               ;; the sum of the number of unique values in previous columns plus
                               ;; the rank of the value in the sorted set of values for this column
                               ;; plus one.
                               [(inc (+ prev-col-counts* (avl/rank-of col-unique-vals val)))
                                (inc row-index)])
                             col-vals)))
            value-cols unique-vals)))

(defn create-texts [cols cat-codes]
  "Creates data to import into the texts table. The returned vectors will
  contain either a startpos and an endpos and nil for the bounds, or a bounds
  value and nil for the other two, depending on whether the metadata defines
  either a bounds category (typically used with speech corpora) or both
  startpos and endpos categories (typically used with written corpora)."
  (let [startpos-index (.indexOf cat-codes "startpos")
        endpos-index   (.indexOf cat-codes "endpos")
        bounds-index   (.indexOf cat-codes "bounds")]
    (assert (or (and (= -1 startpos-index) (= -1 (endpos-index) (not= -1 bounds-index)))
                (and (not= -1 startpos-index (not= -1 endpos-index) (= -1 bounds-index))))
            "Metadata should contain either a bounds category or startpos and endpos categories")
    (if (not= -1 bounds-index)
      (map (fn [bounds] [nil nil bounds]) (nth cols bounds-index))
      (map (fn [startpos endpos] [startpos endpos nil])
           (nth cols startpos-index) (nth cols endpos-index)))))

(defn- create-import-data [value-tsv-path cat-tsv-path]
  (with-open [value-tsv-file (io/reader value-tsv-path)
              cat-tsv-file   (io/reader cat-tsv-path)]
    (let [cat-codes    (->> (utils/read-csv cat-tsv-file) (map first))
          ;; Find the row indexes of the actual metadata categories in the category file,
          ;; which will correspond to the column indexes of those categories in the value file.
          cat-indexes  (set (keep-indexed (fn [index cat]
                                            (when-not (#{"id" "startpos" "endpos" "bounds"} cat)
                                              index))
                                          cat-codes))
          ;; Convert rows to columns
          cols         (->> (utils/read-csv value-tsv-file) (apply map list) )
          ;; This only includes columns with values for actual metadata categories
          value-cols  (keep-indexed (fn [index col] (when (cat-indexes index) col)) cols)
          ;; Get a seq containing sorted sets of unique values for each column
          unique-vals  (map (partial apply avl/sorted-set-by String/CASE_INSENSITIVE_ORDER)
                            value-cols)
          ;; Get import data for the metadata_values table
          value-data   (create-value-data unique-vals)
          ;; Get import data for the metadata_values_texts table
          values-texts (create-value-text-joins value-cols unique-vals)
          ;; Get import data for the texts table
          texts        (create-texts cols cat-codes)]
      [value-data values-texts texts])))

(defn write-import-tsv [value-tsv-path cat-tsv-path]
  (with-open [values-tsv-file       (io/writer (str (fs/tmpdir) "/metadata_values.tsv"))
              values-texts-tsv-file (io/writer (str (fs/tmpdir) "/metadata_values_texts.tsv"))
              texts-tsv-file        (io/writer (str (fs/tmpdir) "/texts.tsv"))]
    (let [[values values-texts texts] (create-import-data value-tsv-path cat-tsv-path)]
      (utils/write-csv values-tsv-file values)
      (utils/write-csv values-texts-tsv-file values-texts)
      (utils/write-csv texts-tsv-file texts))))
