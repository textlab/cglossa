(ns cglossa.search.download
  (require [dk.ative.docjure.spreadsheet :as s]
           [me.raynes.fs :as fs]
           [clojure.data.csv :as csv]
           [clojure.java.io :as io]))

(defn excel-file [search-id headers? rows]
  (let [wb         (s/create-workbook "Search results"
                                      (if headers?
                                        (cons ["Corpus position" "Sentence ID"
                                               "Left context" "Match" "Right context"] rows)
                                        rows))
        sheet      (s/select-sheet "Search results" wb)
        header-row (when headers?
                     (first (s/row-seq sheet)))
        filename   (fs/temp-name (str "glossa-" search-id "-") ".xlsx")]
    (when header-row
      (s/set-row-style! header-row (s/create-cell-style! wb {:background :yellow,
                                                             :font       {:bold true}})))
    ;; TODO: Figure out why this does not have any effect (even when we do the formatting
    ;; before adding any rows to the sheet)
    #_(let [cell-style (.createCellStyle wb)]
        (.setAlignment cell-style org.apache.poi.ss.usermodel.HorizontalAlignment/RIGHT)
        (.setDefaultColumnStyle sheet 0 cell-style)
        (s/add-rows! sheet rows))
    (.setColumnWidth sheet 1 5000)
    (.setColumnWidth sheet 2 20000)
    (s/save-workbook! (str "resources/public/tmp/" filename) wb)
    (str "tmp/" filename)))

(defn csv-file [type search-id headers? rows]
  (let [filename (fs/temp-name (str "glossa-" search-id "-") (if (= type :tsv) ".tsv" ".csv"))
        rows*    (if headers?
                   (cons ["Corpus position" "Sentence ID" "Left context" "Match" "Right context"]
                         rows)
                   rows)]
    (with-open [file (io/writer (str "resources/public/tmp/" filename))]
      (csv/write-csv file rows* :separator (if (= type :tsv) \tab \,)))
    (str "tmp/" filename)))
