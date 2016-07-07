(ns cglossa.routes
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.route :refer [resources]]
            [korma.db :as kdb]
            [cheshire.core :as cheshire]
            [ring.util.response :as response]
            [ring.handler.dump :refer [handle-dump]]
            [cognitect.transit :as transit]
            [net.cgrand.enlive-html :refer [deftemplate html-content]]
            [taoensso.timbre :as timbre]
            [cglossa.shared :refer [corpus-connections]]
            [cglossa.db.corpus :refer [get-corpus]]
            [cglossa.db.metadata :refer [get-metadata-categories get-metadata-values
                                         show-texts num-selected-texts result-metadata]]
            [cglossa.search.core :refer [search-corpus results geo-distr]]
            [cglossa.search.cwb.speech :refer [play-video]])
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

(deftemplate page (io/resource "index.html") [])
(deftemplate admin (io/resource "admin.html") []
  [:#corpus-table]
  (html-content
    (str "<tr><td style=\"width: 350px;\">AA</td>"
         "<td><button class=\"btn btn-danger btn-xs\">Delete</td></tr>")))

(defroutes app-routes
  (resources "/")
  (GET "/request" [] handle-dump)
  (GET "/" req (page))
  (GET "/admin" req (admin)))

(defroutes db-routes
  (GET "/corpus" [code]
    (if-let [c (get-corpus {:code code})]
      (let [cats (kdb/with-db (get @corpus-connections (:id c)) (get-metadata-categories))]
        (transit-response {:corpus              c
                           :metadata-categories cats}))
      {:status 500
       :body   (str "No corpus named " code " exists!")}))

  (POST "/corpus" [zipfile]
    (println zipfile))

  (GET "/metadata-values" [category-id value-filter selected-ids page]
    (let [selected-ids* (when selected-ids (cheshire/parse-string selected-ids))
          page*         (if page (Integer/parseInt page) 1)
          data          (get-metadata-values category-id value-filter selected-ids* page*)]
      (-> (response/response (cheshire/generate-string {:results    (:results data)
                                                        :pagination {:more (:more? data)}}))
          (response/content-type "application/json")
          (response/charset "utf-8"))))

  (POST "/texts" [selected-metadata ncats page]
    (let [ncats* (or ncats 1)
          page*  (or page 1)]
      (transit-response (show-texts selected-metadata ncats* page*) false)))

  (POST "/num-texts" [selected-metadata]
    (transit-response (num-selected-texts selected-metadata))))

(defroutes search-routes
  (POST "/search" [corpus-id search-id queries metadata-ids step page-size last-count sort-key]
    (transit-response (search-corpus corpus-id search-id queries metadata-ids
                                     step page-size last-count sort-key) false))

  (GET "/results" [corpus-id search-id start end cpu-counts sort-key]
    (transit-response (results corpus-id search-id start end cpu-counts sort-key) false))

  (GET "/result-metadata" [corpus-id text-id]
    (transit-response (result-metadata (Integer/parseInt corpus-id) text-id) false))

  (GET "/play-video" [corpus-id search-id result-index context-size]
    (transit-response (play-video corpus-id search-id result-index context-size) false))

  (POST "/geo-distr" [corpus-id search-id metadata-ids]
    (transit-response (geo-distr corpus-id search-id metadata-ids) false)))
