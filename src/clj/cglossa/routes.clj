(ns cglossa.routes
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [environ.core :refer [env]]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.route :refer [resources]]
            [korma.db :as kdb]
            [cheshire.core :as cheshire]
            [ring.util.response :as response]
            [ring.handler.dump :refer [handle-dump]]
            [cognitect.transit :as transit]
            [net.cgrand.enlive-html :refer [deftemplate html-content]]
            [clojure.tools.logging :as log]
            [cglossa.shared :refer [corpus-connections]]
            [cglossa.db.corpus :refer [corpus-by-code]]
            [cglossa.db.metadata :refer [get-metadata-categories get-metadata-values show-texts]]
            [cglossa.search.core :as search])
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
         res    (.toString baos)]
     (.reset baos)
     (-> (response/response res)
         (response/content-type "application/transit+json")
         (response/charset "utf-8")))))

(defmacro transit-response [fn-call & args]
  `(try (let [res# ~fn-call]
          (transit-response* res# ~@args))
        (catch Exception e#
          (log/error e#)
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
    (if-let [c (corpus-by-code code)]
      (let [cats    (kdb/with-db (get @corpus-connections (:id c)) (get-metadata-categories))
            tagfile (case (:code c)
                      "gigaword_fre_3" "treetagger_fr"
                      "obt_bm_lbk")
            [gram-titles & menu-data] (read-string (slurp (str "resources/taggers/"
                                                               tagfile ".edn")))]
        (transit-response {:corpus              c
                           :metadata-categories cats
                           :gram-titles         gram-titles
                           :menu-data           menu-data}))
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
      (transit-response (show-texts selected-metadata ncats* page*)))))

(defroutes search-routes
  (POST "/search" [corpus-id search-id queries metadata-ids step cut sort-key]
    (transit-response (search/search-corpus corpus-id search-id queries metadata-ids
                                            step cut sort-key) false))

  (GET "/results" [corpus-id search-id start end sort-key]
    (transit-response (search/results corpus-id search-id start end sort-key) false)))

