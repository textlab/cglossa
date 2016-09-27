(ns cglossa.search.download
  (require [dk.ative.docjure.spreadsheet :as s]
           [me.raynes.fs :as fs]))

(defn excel-file [search-id data]
  (let [rows       (for [line data]
                     ;; Extract corpus position, sentence/utterance ID, left context, match and right
                     ;; context from the result line
                     (rest (re-find #"^\s*(\d+):\s*<.+?\s(.+?)>:\s*(.+?)\s*\{\{(.+?)\}\}\s+(.+)"
                                    line)))
        wb         (s/create-workbook "Search results"
                                      (cons ["Corpus position" "Sentence ID"
                                             "Left context" "Match" "Right context"] rows))
        sheet      (s/select-sheet "Search results" wb)
        header-row (first (s/row-seq sheet))
        filename   (fs/temp-name (str "glossa-" search-id "-") ".xlsx")]
    (s/set-row-style! header-row (s/create-cell-style! wb {:background :yellow,
                                                           :font       {:bold true}}))
    (s/save-workbook! (str "resources/public/tmp/" filename) wb)
    (str "tmp/" filename)))
