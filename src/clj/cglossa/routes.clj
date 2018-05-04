(ns cglossa.routes
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.route :refer [resources files]]
            [korma.db :as kdb]
            [korma.core :refer [defentity table select insert delete values fields where raw join]]
            [cheshire.core :as cheshire]
            [ring.util.response :as response]
            [ring.handler.dump :refer [handle-dump]]
            [cognitect.transit :as transit]
            [net.cgrand.enlive-html :refer [deftemplate html-content clone-for set-attr content
                                            xml-resource transform substitute]]
            [taoensso.timbre :as timbre]
            [cglossa.shared :refer [core-db corpus-connections]]
            [cglossa.search.cwb.shared :refer [token-count-matching-metadata]]
            [cglossa.db.corpus :refer [get-corpus]]
            [cglossa.db.metadata :refer [get-metadata-categories get-metadata-values
                                         show-texts num-selected-texts result-metadata]]
            [cglossa.search.core :refer [stats-corpus results geo-distr download-results]]
            [cglossa.db.corpus :refer [corpus]]
            [cglossa.search.cwb.speech :refer [play-video]]
            [cglossa.corpora :refer [text-selection-info]]
            [cglossa.search.shared :refer [search-corpus]]
            [cglossa.search.cwb.speech :refer [play-video]]
            [cglossa.search.fcs :as fcs])
  (:import (java.io ByteArrayOutputStream)))

(defn- hyphenize-keys
  "Recursively changes underscore to hyphen in all map keys, which are assumed
   to be strings or keywords."
  [m]
  (let [f (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v])]
    (walk/postwalk #(if (map? %) (into {} (map f %)) %) m)))

(defn- transit-response*
  ([body]
   (transit-response* body true))
  ([body hyphenize?]
   (let [baos   (ByteArrayOutputStream. 2000)
         writer (transit/writer baos (if (:is-dev env) :json-verbose :json))
         _      (transit/write writer (if hyphenize? (hyphenize-keys body) body))
         res    (str baos)]
     (.reset baos)
     (-> (response/response res)
         (response/content-type "application/transit+json")
         (response/charset "utf-8")))))

(defmacro transit-response [fn-call & args]
  `(try (let [res# ~fn-call]
          (transit-response* res# ~@args))
        (catch Exception e#
          (timbre/error e#)
          {:status 500
           :body   (.toString e#)})))

(deftemplate page (io/resource "index.html") [corpus-code]
  [:#corpus-info-header]
  (fn [node]
    (let [google-analytics-id (or (-> (kdb/with-db core-db
                                        (select corpus
                                                (fields :google_analytics_id)
                                                (where {:code corpus-code})))
                                      first
                                      :google_analytics_id)
                                  "ID")]
      [{:tag     :script
        :attrs   {:async true
                  :src   (str "https://www.googletagmanager.com/gtag/js?id="
                              google-analytics-id)}
        :content []}
       {:tag     :script
        :content (str "window.dataLayer = window.dataLayer || [];"
                      "function gtag(){dataLayer.push(arguments);}"
                      "gtag('js', new Date());"
                      "gtag('config', '" google-analytics-id "');")}])))
(deftemplate front (io/resource "front.html") []
  [:#corpus-entry] (clone-for [corp (->> (kdb/with-db core-db (select corpus
                                                                      (fields :code :name)
                                                                      (where {:hidden false})))
                                         (sort-by #(str/replace (:name %) #"^The\s+" "")))]
                              [:li :a] (content (-> corp
                                                    :name
                                                    (str/replace #":\s*<br/?>" ": ")
                                                    (str/replace #"<br/?>" "; ")))
                              [:li :a] (set-attr :href (:code corp))))

(deftemplate admin (io/resource "admin.html") []
  [:#corpus-table]
  (html-content
    (str "<tr><td style=\"width: 350px;\">AA</td>"
         "<td><button class=\"btn btn-danger btn-xs\">Delete</td></tr>")))

(deftemplate xml-results (xml-resource "results.xml") [corpus-code cnt results]
  [:sru:numberOfRecords] (html-content cnt)
  [:sru:record] (clone-for [i (range (count results))]
                           (substitute (-> (xml-resource "sru_record.xml")
                                           (transform
                                             [:fcs:Resource]
                                             (set-attr :pid
                                                       (str "https://tekstlab.uio.no/glossa2/fcs/#"
                                                            (:s_id (nth results i)))))
                                           (transform
                                             [:hits:Result]
                                             (html-content (str (:left (nth results i))
                                                                " <hits:Hit>"
                                                                (:keyword (nth results i))
                                                                "</hits:Hit>"
                                                                (:right (nth results i)))))
                                           (transform
                                             [:sru:recordPosition]
                                             (html-content (inc i)))))))

(defroutes app-routes
  (files "/" {:root "resources/public" :mime-types {"tsv" "text/tab-separated-values"}})
  (files "/:corpus-code/" {:root "resources/public" :mime-types {"tsv" "text/tab-separated-values"}})
  (resources "/" {:mime-types {"tsv" "text/tab-separated-values"}})
  (resources "/:corpus-code/" {:mime-types {"tsv" "text/tab-separated-values"}})
  (GET "/:corpus-code/request" [] handle-dump)
  ;(GET "/admin" req (admin))
  (GET "/" [] (front)))

(defroutes db-routes
  (GET "/:corpus-code/corpus" [corpus-code :as {user-data :user-data}]
    (let [c (get-corpus {:code corpus-code})]
      (if (:id c)
        (let [cats (kdb/with-db (get @corpus-connections corpus-code) (get-metadata-categories))]
          (transit-response {:corpus              c
                             :metadata-categories cats
                             :authenticated-user  (or (:displayName user-data)
                                                      (:mail user-data)
                                                      (:eduPersonPrincipalName user-data)
                                                      (:eduPersonTargetedID user-data)
                                                      (:id user-data))}))
        {:status 404
         :body   (str "Corpus '" corpus-code "' not found.")})))

  #_(POST "/corpus" [zipfile]
      (println zipfile))

  (GET "/:corpus-code/metadata-values" [category-id value-filter selected-ids page]
    (let [selected-ids* (when selected-ids (cheshire/parse-string selected-ids))
          page*         (if page (Integer/parseInt page) 1)
          data          (get-metadata-values category-id value-filter selected-ids* page*)]
      (-> (response/response (cheshire/generate-string {:results    (:results data)
                                                        :pagination {:more (:more? data)}}))
          (response/content-type "application/json")
          (response/charset "utf-8"))))

  (POST "/:corpus-code/texts" [selected-metadata page sort-column-id sort-ascending?]
    (let [page* (or page 1)]
      (transit-response (show-texts selected-metadata page* sort-column-id sort-ascending?) false)))

  (POST "/:corpus-code/num-texts" [selected-metadata-ids]
    (transit-response (num-selected-texts selected-metadata-ids)))

  (POST "/:corpus-code/num-tokens" [corpus-code queries selected-metadata-ids]
    (let [corpus (get-corpus {:code corpus-code})]
      (transit-response (token-count-matching-metadata corpus queries selected-metadata-ids))))

  (POST "/:corpus-code/text-selection-info" [corpus-code selected-metadata-ids]
    (let [corpus (get-corpus {:code corpus-code})]
      (transit-response (text-selection-info corpus selected-metadata-ids)))))

(defroutes search-routes
  (POST "/:corpus-code/search" [corpus-code search-id queries metadata-ids step page-size last-count
                                context-size sort-key num-random-hits random-hits-seed]
    (transit-response (search-corpus corpus-code search-id queries metadata-ids
                                     step page-size last-count context-size sort-key
                                     num-random-hits random-hits-seed) false))

  (POST "/:corpus-code/stats" [corpus-code search-id queries metadata-ids step page-size last-count
                               context-size sort-key num-random-hits random-hits-seed
                               freq-attr freq-case-sensitive]
    (transit-response (stats-corpus corpus-code search-id queries metadata-ids
                                    step page-size last-count context-size sort-key
                                    num-random-hits random-hits-seed freq-attr freq-case-sensitive)
                      false))

  (GET "/:corpus-code/results" [corpus-code search-id start end cpu-counts context-size sort-key]
    (transit-response (results corpus-code search-id start end cpu-counts context-size sort-key)
                      false))

  (GET "/:corpus-code/result-metadata" [corpus-code text-id]
    (transit-response (result-metadata corpus-code text-id) false))

  (GET "/:corpus-code/play-video" [corpus-code search-id result-index context-size]
    (transit-response (play-video corpus-code search-id result-index context-size) false))

  (POST "/:corpus-code/geo-distr" [corpus-code search-id metadata-ids]
    (transit-response (geo-distr corpus-code search-id metadata-ids) false))

  (POST "/:corpus-code/download-results" [corpus-code search-id cpu-counts format headers?
                                          attrs context-size num-random-hits]
    (download-results corpus-code search-id cpu-counts format headers?
                      attrs context-size num-random-hits))

  (GET "/fcs/:corpus-code" [corpus-code operation query maximumRecords]
    (let [{:keys [cnt results]} (fcs/search-local corpus-code operation query maximumRecords)]
      {:status  200
       :headers {"Content-Type" "text/xml;charset=utf-8"}
       :body    (xml-results corpus-code cnt results)})))

;; NOTE: Since this route does not specify anything other than the fact that the URL only contains
;; one part, it should be the last route we attempt to match to avoid "swallowing" other URLs
(defroutes corpus-home
  (GET "/:corpus-code" [corpus-code] (page corpus-code)))
