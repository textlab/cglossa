(ns cglossa.data-import.metadata-values
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [cglossa.data-import.utils :as utils]))

(defn create-import-tsvs [value-tsv-path cat-tsv-path]
  (with-open [value-tsv-file (io/reader value-tsv-path)
              cat-tsv-file   (io/reader cat-tsv-path)]
    ;; Find the row indexes of the actual metadata categories in the category file,
    ;; which will correspond to the column indexes of those categories in the value file.
    (let [cat-indexes (set (keep-indexed (fn [index cat]
                                           (when-not (#{"id" "startpos" "endpos" "bounds"} cat)
                                             index))
                                         (->> (utils/read-csv cat-tsv-file)
                                              (map first))))]
      (->> (utils/read-csv value-tsv-file)
           ;; Convert rows to columns
           (apply map list)
           ;; Only include columns with values for actual metadata categories
           (keep-indexed (fn [index col] (when (cat-indexes index) col)))
           ;; Concatenate the category ids and values from each column
           (map-indexed (fn [index col]
                          ;; Get the sorted set of unique values in this column
                          (let [unique-vals (apply sorted-set col)]
                            ;; Return the metadata category id and the metadata value.
                            ;; The metadata category table is always truncated before import,
                            ;; so we know that the category ids start at 1.
                            (map (fn [val] [(inc index) val]) unique-vals))))
           (apply concat)))))

(defn write-import-tsv [value-tsv-path cat-tsv-path]
  (with-open [values-tsv (io/writer (str (fs/tmpdir) "/metadata_values.tsv"))]
    (utils/write-csv values-tsv (create-import-tsvs value-tsv-path cat-tsv-path))))
