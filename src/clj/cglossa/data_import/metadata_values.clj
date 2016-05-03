(ns cglossa.data-import.metadata-values
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [clojure.data.avl :as avl]
            [cglossa.data-import.utils :as utils])
  (:gen-class))

(defn- create-value-data
  "Creates seqs of [category-id, value] vectors from each column and
  concatenate them; this will be imported into the metadata_values table."
  [unique-vals]
  (->> unique-vals
       (map-indexed (fn [index col-unique-vals]
                      ;; Return the metadata category id and the metadata value. The metadata
                      ;; category table is always truncated before import, so we know that the
                      ;; category ids (which is equal to the value column number) start at 1.
                      (map (fn [val] [(inc index) val]) col-unique-vals)))
       (apply concat)))

(defn- create-value-text-joins
  "Creates data to import into the metadata_values_texts join table, linking
  metadata values and texts in a many-to-many relationship."
  [value-cols unique-vals]
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

(defn create-texts
  "Creates data to import into the texts table. The returned lists will
  contain either a startpos and an endpos or a bounds value, depending on
  whether the metadata defines either a bounds category (typically used with
  speech corpora) or both startpos and endpos categories (typically used with
  written corpora). For multilingual corpora, there should also be a language
  column that indicates which aligned language the text belongs to (can also be
  used for other types of aligned corpus parts)."
  [cols cat-codes]
  (let [startpos-index (.indexOf cat-codes "startpos")
        endpos-index   (.indexOf cat-codes "endpos")
        bounds-index   (.indexOf cat-codes "bounds")
        language-index (.indexOf cat-codes "language")]
    (assert (or (and (= -1 startpos-index) (= -1 endpos-index (not= -1 bounds-index)))
                (and (not= -1 startpos-index (not= -1 endpos-index) (= -1 bounds-index))))
            "Metadata should contain either a bounds category or startpos and endpos categories")
    (if (not= -1 bounds-index)
      (if (not= -1 language-index)
        (map list (nth cols bounds-index) (nth cols language-index))
        (map list (nth cols bounds-index)))
      (if (not= -1 language-index)
        (map list (nth cols startpos-index) (nth cols endpos-index) (nth cols language-index))
        (map list (nth cols startpos-index) (nth cols endpos-index))))))

(defn- create-import-data [value-tsv-path cat-tsv-path]
  (with-open [value-tsv-file (io/reader value-tsv-path)
              cat-tsv-file   (io/reader cat-tsv-path)]
    (let [cat-codes    (map first (utils/read-csv cat-tsv-file))
          ;; Find the row indexes of the actual metadata categories in the category file,
          ;; which will correspond to the column indexes of those categories in the value file.
          cat-indexes  (set (keep-indexed (fn [index cat]
                                            (when-not (#{"id" "startpos" "endpos" "bounds"} cat)
                                              index))
                                          cat-codes))
          ;; Convert rows to columns
          cols         (apply map list (utils/read-csv value-tsv-file))
          ;; This only includes columns with values for actual metadata categories
          value-cols   (keep-indexed (fn [index col] (when (cat-indexes index) col)) cols)
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

(defn -main [value-tsv-path cat-tsv-path]
  (with-open [values-tsv-file       (io/writer (str (fs/tmpdir) "/glossa/metadata_value.tsv"))
              values-texts-tsv-file (io/writer (str (fs/tmpdir) "/glossa/metadata_value_text.tsv"))
              texts-tsv-file        (io/writer (str (fs/tmpdir) "/glossa/text.tsv"))]
    (let [[values values-texts texts] (create-import-data value-tsv-path cat-tsv-path)]
      (utils/write-csv values-tsv-file values)
      (utils/write-csv values-texts-tsv-file values-texts)
      (utils/write-csv texts-tsv-file texts))))
