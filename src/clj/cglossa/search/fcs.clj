(ns cglossa.search.fcs
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [cglossa.search.shared :refer [search-corpus run-queries]]
            [cglossa.search.core :refer [get-results]]
            [org.httpkit.client :as http]))

(def SRUCQL_VERSION "1.2")

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
  "Called when we receive an FCS search request from an external agent to a
   corpus stored locally in Glossa."
  (if (= operation "searchRetrieve")
    (let [queries     (create-queries query)
          max-records (or (Integer/parseInt max-records) 1000)
          results     (search-corpus corpus-code nil queries nil 1 max-records nil 20 :position)]
      {:results (extract-data-from (take max-records (:results results)))
       :cnt     (:count results)})
    {:status  422
     :headers {}
     :body    "No recognizable operation provided"}))


(defn- do-remote-search [corpus queries start end]
  (let [codes   (->> corpus :remote_urls edn/read-string (map :code))
        urls    (->> corpus :remote_urls edn/read-string (map :url))
        query   (->> queries first :query (re-find #"word=\"(.*?)\"") second)
        params  {:version        SRUCQL_VERSION
                 :operation      "searchRetrieve"
                 :query          query
                 :startRecord    start
                 :maximumRecords end}
        ;; send the request concurrently (asynchronously)
        futures (doall (map #(http/get % {:as           :text,
                                          :headers      {"Accept" "text/html,application/xhtml+xml,application/xml"}
                                          :query-params params})
                            urls))
        ;; wait for server response synchronously
        bodies  (map #(:body (deref %)) futures)
        ;; add up the total number of hits reported to exist in each corpus
        ;; (NOTE: certain corpora mistakenly report the *requested* number of
        ;; hits instead)
        cnt     (reduce (fn [acc body]
                          (+ acc (->> body
                                      (re-find #"(?<=<sru:numberOfRecords>)\d+")
                                      (Integer/parseInt))))
                        0 bodies)
        hits    (->> bodies
                     (map (partial re-seq #"(?s)<(\w+:)?Resource(.+?)<\/\1Resource>"))
                     (map (partial take (/ (/ (- end start) 2) (count bodies))))
                     (map-indexed
                       (fn [index body-resources]
                         (map
                           (fn [resource]
                             (let [text  (nth resource 2)
                                   pid   (second (re-find #"pid=\"(.*?)\"" text))
                                   s-id  (str (nth codes index) ":" pid)
                                   left  (nth (re-find #"(?s)<(\w+:)?c type=\"left\">(.+?)</(\w+:)?c>" text) 2)
                                   match (nth (re-find #"(?s)<(\w+:)?kw>(.+?)</(\w+:)?kw>" text) 2)
                                   right (nth (re-find #"(?s)<(\w+:)?c type=\"right\">(.+?)</(\w+:)?c>" text) 2)]
                               (str "0: <s_id " s-id ">: " left " {{" match "}} " right)))
                           body-resources)))
                     (apply concat))]
    [hits cnt [cnt]]))


(defmethod run-queries "fcs"
  [corpus search-id queries metadata-ids step
   page-size last-count context-size sort-key num-random-hits cmd]
  (do-remote-search corpus queries 1 (* 2 page-size)))

(defmethod get-results ["fcs" nil] [corpus _ queries start end _ _ _ _]
  (do-remote-search corpus queries start end))
