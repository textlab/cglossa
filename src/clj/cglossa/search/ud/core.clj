(ns cglossa.search.ud.core
  "Support for searching the Universal Dependency treebanks
  (http://universaldependencies.github.io/docs/) using the SETS system
  at the University of Turku (http://bionlp-www.utu.fi/dep_search)."
  (:require [me.raynes.fs :as fs]
            [korma.core :as korma]
            [net.cgrand.enlive-html :as html]
            [cemerick.url :as url]
            [cglossa.search.core :refer [run-queries get-results transform-results]]
            [clojure.string :as str]))

(defmethod run-queries "ud" [corpus search queries metadata-ids step cut sort-by]
  (condp = step
    2 ["1"]
    3 ["1"]
    1 [(-> (str "http://bionlp-www.utu.fi/dep_search/?db=English&search="
                (ring.util.codec/form-encode (apply str (interpose " " (map second (re-seq #"word=\"(.+?)\"" (:query (first queries))))))))
           slurp
           (str/replace "src=\"js" "src=\"http://bionlp-www.utu.fi/dep_search/js")
           (str/replace "var root = ''" "var root = 'http://bionlp-www.utu.fi/dep_search/'")
           (str/replace "<form class=\"query-form\"" "<form class=\"query-form\" style=\"display:none\"")
           (str/replace "nav class=\"navbar" "nav style=\"display:none\" class=\"navbar"))]))
