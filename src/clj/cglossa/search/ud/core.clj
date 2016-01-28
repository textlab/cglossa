(ns cglossa.search.ud.core
  "Support for searching the Universal Dependency treebanks
  (http://universaldependencies.github.io/docs/) using the SETS system
  at the University of Turku (http://bionlp-www.utu.fi/dep_search)."
  (:require [ring.util.codec :as codec]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [clojure.string :as str]))

(defmethod run-queries "ud" [corpus search queries metadata-ids step cut sort-key]
  (let [q       (first queries)
        lang    (or (:lang q) "English")
        text    (-> (str "http://bionlp-www.utu.fi/dep_search/?db=" lang
                         "&search=" (codec/form-encode (:query q)))
                    slurp)
        results [(-> text
                     (str/replace "src=\"js"
                                  "src=\"http://bionlp-www.utu.fi/dep_search/js")
                     (str/replace "var root = ''"
                                  "var root = 'http://bionlp-www.utu.fi/dep_search/'")
                     (str/replace "<form class=\"query-form\""
                                  "<form class=\"query-form\" style=\"display:none\"")
                     (str/replace "nav class=\"navbar"
                                  "nav style=\"display:none\" class=\"navbar"))]
        cnt     (count (re-seq #"<code class=\"conllu\"" text))]
    [results cnt]))
