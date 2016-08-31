(ns cglossa.result-views.cwb.written
  (:require [clojure.string :as str]
            [reagent.core :as r :include-macros true]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [concordance-table]]
            [cglossa.result-views.cwb.shared :as shared]))

(defn- process-field [displayed-field-index field]
  "Processes a pre-match, match, or post-match field."
  (as-> field $
        (str/split $ #"\s+")
        (keep-indexed (fn [index token]
                        (if (str/includes? token \/)
                          (let [attrs    (str/split token #"/")
                                tip-text (str/join " " (as-> attrs $
                                                             (rest $)
                                                             (remove #(get #{"__UNDEF__" "-"} %) $)
                                                             (vec $)
                                                             ;; Show the lemma in quotes
                                                             (update $ 0 #(str "\"" % "\""))))]
                            ^{:key index}
                            [:span {:data-toggle "tooltip"
                                    :title       tip-text
                                    :data-html   true}
                             (get attrs displayed-field-index) " "])
                          ;; With multi-word expressions, the non-last parts become simple strings
                          ;; without any attributes (i.e., no slashes) when we split the text on
                          ;; whitespace. Just print out those non-last parts and leave the tooltip
                          ;; to be attached to the last part.
                          ^{:key index} [:span token " "]))
                      $)))

(defn- non-first-multilingual [index line]
  ;; Extract the IDs of all s-units (typically sentences)
  ;; and put them in front of their respective s-units.
  (let [matches    (re-seq #"<(\w+_id)\s*(.+?)>(.*?)</\1>" line)
        components (if matches
                     (map (fn [m]
                            (list [:span.aligned-id (nth m 2)] ": " (nth m 3)))
                          matches)
                     (process-field 0 line))]
    ^{:key index} [:tr [:td] [:td {:col-span 3} components]]))

(defn- extract-fields [res]
  (let [m (re-find #"^<s_id\s+(.*?)>:\s+(.*)\{\{(.+?)\}\}(.*?)$" res)]
    (let [[_ s-id pre match post] m]
      [(str/trim s-id) [pre match post]])))

(defn- main-row [a {:keys [corpus] :as m} result index]
  ^{:key (hash result)}
  [:tr
   (shared/id-column a m result index)
   (shared/text-columns result)])

(defn- original-row [a {:keys [corpus] :as m} result index]
  ^{:key (str "orig" index)}
  [:tr
   [:td {:style {:vertical-align "middle"}}]
   (shared/text-columns result)])

(defn single-result-rows [a m word-index orig-index res index]
  "Returns one or more rows representing a single search result."
  (let [[main-line & other-lines] (:text res)
        [s-id fields] (extract-fields main-line)
        [pre match post] (map (partial process-field word-index) fields)
        [orig-pre orig-match orig-post] (when orig-index
                                          (map (partial process-field orig-index) fields))
        res-info {:word {:s-id       s-id
                         :pre-match  pre
                         :match      match
                         :post-match post}
                  :orig {:s-id       s-id
                         :pre-match  orig-pre
                         :match      orig-match
                         :post-match orig-post}}
        main     (main-row a m (:word res-info) index)
        orig     (when orig-index
                   [(original-row a m (:orig res-info) index)
                    (shared/separator-row index)])
        others   (map-indexed non-first-multilingual other-lines)]
    ;; Assume that we have EITHER an attribute with original text OR several other langage rows
    (cons main (or orig others))))

(defmethod concordance-table :default
  [{{:keys [results page-no]} :results-view :as a} {:keys [corpus] :as m}]
  (let [res (get @results @page-no)]
    [:div.row>div.col-sm-12.search-result-table-container
     [b/table {:striped true :bordered true}
      [:tbody
       (let [attrs      (->> @corpus :languages first :config :displayed-attrs (map first))
             word-index 0               ; word form is always the first attribute
             ;; We need to inc orig-index since the first attribute ('word') is
             ;; not in the list because it is shown by default by CQP
             orig-index (first (keep-indexed #(when (= %2 :orig) (inc %1)) attrs))]
         (doall (map (partial single-result-rows a m word-index orig-index)
                     res
                     (range (count res)))))]]]))
