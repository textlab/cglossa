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
  (let [q (-> query
              (str/replace "\"" "")
              (str/split #"\s+")
              (map (fn [term]
                     (let [res (re-find #"^prox\/unit=word\/distance(\D+)(\d+)")]
                       (if res
                         (interval-specification (first res) (Integer/parseInt (second res)))
                         (str "[word=\"" term "\" %c]")))))
              (str/join " "))]
    [{:lang "single" :query q}])
  )

(defn search-local [corpus-code operation query max-records]
  (if (= operation "searchRetrieve")
    (let [queries (create-queries query)]
      (println (search-corpus corpus-code nil queries nil 1 (or max-records 10) nil 20 :position)))
    {:status 422
     :headers {}
     :body "No recognizable operation provided"}))


