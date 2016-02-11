(ns cglossa.result-views.cwb.written
  (:require [clojure.string :as str]
            [reagent.core :as r :include-macros true]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [concordance-table]]
            [cglossa.result-views.cwb.shared :as shared]))

(defn- monolingual-or-first-multilingual [res]
  (let [m (re-find #"<(\w+_(?:id|name))(.*?)>(.*)\{\{(.+?)\}\}(.*?)</\1>$" (:text res))]
    ;; There will only be a surrounding structural attribute if the corpus has some
    ;; kind of s-unit segmentation
    (if m
      (let [[_ _ s-id pre match post] m]
        [(str/trim s-id) [pre match post]])
      ;; Try again without the surrounding structural attribute
      (let [m (re-find #"(.*)\{\{(.+?)\}\}(.*)" (:text res))
            [_ pre match post] m]
        ["" [pre match post]]))))

(defn- non-first-multilingual [res]
  ;; Extract the IDs of all s-units (typically sentences)
  ;; and put them in front of their respective s-units.
  (let [text (str/replace res
                          #"<(\w+_id)\s*(.+?)>(.*?)</\1>"
                          "<span class=\"aligned-id\">$2</span>: $3")]
    [nil [text]]))

(defn- extract-fields [res]
  (let [m (re-find #"^<s_id\s+(.*?)>:\s+(.*)\{\{(.+?)\}\}(.*?)$" (:text res))]
    (let [[_ s-id pre match post] m]
      [(str/trim s-id) [pre match post]])))

(defn- main-row [result index a {:keys [corpus] :as m}]
  ^{:key (hash result)}
  [:tr
   (shared/id-column result)
   (shared/text-columns result)])

(defn- process-field [field]
  "Processes a pre-match, match, or post-match field."
  (as-> field $
        (str/split $ #"\s+")
        (map-indexed (fn [index token]
                       (let [attrs    (str/split token #"/")
                             tip-text (str/join " " (->> attrs rest (remove #(= % "__UNDEF__"))))]
                         ^{:key index}
                         [:span {:data-toggle "tooltip"
                                 :title       tip-text}
                          (first attrs) " "]))
                     $)))

(defn single-result-rows [a m res index]
  "Returns one or more rows representing a single search result."
  (let [[s-id fields] (extract-fields res)
        [pre match post] (map process-field fields)
        res-info {:s-id       s-id
                  :pre-match  pre
                  :match      match
                  :post-match post}
        main     (main-row res-info index a m)]
    main))

(defmethod concordance-table :default [{{:keys [results page-no]} :results-view :as a} m]
  (let [res (get @results @page-no)]
    [:div.row>div.col-sm-12.search-result-table-container
     [b/table {:striped true :bordered true}
      [:tbody
       (doall (map (partial single-result-rows a m)
                   res
                   (range (count res))))]]]))
