(ns cglossa.search.fcs
  (:require [clojure.string :as str]
            [cglossa.search.shared :refer [search-corpus]]))

(defn- interval-specification [operator num]
  (let [interval (case operator
                   "<" (str "{0," (dec num) "}")
                   "<=" (str "{0," num "}")
                   ">" (str "{" (inc num) ",}")
                   ">=" (str "{" num ",}")
                   ("=", "==") (str "{" num "}"))]))

(defn- create-queries [query]
  ; Remove any enclosing quotes
  (let [q (as-> query $
                (str/replace $ "\"" "")
                (str/split $ #"\s+")
                (map (fn [term]
                       (let [res (re-find #"^prox\/unit=word\/distance(\D+)(\d+)" term)]
                         (if res
                           (interval-specification (first res) (Integer/parseInt (second res)))
                           (str "[word=\"" term "\" %c]"))))
                     $)
                (str/join " " $))]
    [{:lang "single" :query q}]))

(defn- extract-data-from [results]
  (for [result results
        :let [[s_id, left, keyword, right] (->> result
                                                :text
                                                first
                                                (re-find #"<s_id(.*?)>: (.*?)\{\{(.+?)\}\}(.*)")
                                                rest
                                                (map str/trim))]]
    {:s_id s_id :left left :keyword keyword :right right}))

(defn search-local [corpus-code operation query max-records]
  (if (= operation "searchRetrieve")
    (let [queries     (create-queries query)
          max-records (or (Integer/parseInt max-records) 1000)
          results     (search-corpus corpus-code nil queries nil 1 max-records nil 20 :position)]
      {:results (extract-data-from (take max-records (:results results)))
       :cnt (:count results)})
    {:status  422
     :headers {}
     :body    "No recognizable operation provided"}))


