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
            [net.cgrand.enlive-html :refer [deftemplate html-content clone-for set-attr content]]
            [taoensso.timbre :as timbre]
            [buddy.hashers :as hashers]
            [cglossa.shared :refer [core-db corpus-connections]]
            [cglossa.search.cwb.shared :refer [token-count-matching-metadata]]
            [cglossa.db.corpus :refer [get-corpus]]
            [cglossa.db.metadata :refer [get-metadata-categories get-metadata-values
                                         show-texts num-selected-texts result-metadata]]
            [cglossa.search.core :refer [search-corpus results geo-distr download-results]]
            [cglossa.db.corpus :refer [corpus]]
            [cglossa.search.cwb.speech :refer [play-video]])
  (:import (java.io ByteArrayOutputStream)))

(def max-session-age 86400) ; in seconds
(defentity session (table :session))
(defentity user (table :user))

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
(deftemplate front (io/resource "front.html") []
  [:#corpus-entry] (clone-for [corp (kdb/with-db core-db (select corpus (fields :code :name)))]
                              [:li :a] (content (:name corp))
                              [:li :a] (set-attr :href (str "./?corpus=" (:code corp)))))

(deftemplate admin (io/resource "admin.html") []
  [:#corpus-table]
  (html-content
    (str "<tr><td style=\"width: 350px;\">AA</td>"
         "<td><button class=\"btn btn-danger btn-xs\">Delete</td></tr>")))

(defroutes app-routes
  (files "/" {:root "resources/public" :mime-types {"tsv" "text/tab-separated-values"}})
  (resources "/" {:mime-types {"tsv" "text/tab-separated-values"}})
  (GET "/request" [] handle-dump)
  ;(GET "/admin" req (admin))
  (GET "/" {{corpus :corpus} :params} (if corpus (page) (front))))

(defroutes db-routes
  (GET "/corpus" {user-data :user-data params :params}
    (let [code (:code params)
          c    (get-corpus {:code code})]
      (if (:id c)
        (let [cats (kdb/with-db (get @corpus-connections (:id c)) (get-metadata-categories))]
          (transit-response {:corpus              c
                             :metadata-categories cats
                             :authenticated-user  (or (:displayName user-data) (:mail user-data))}))
        {:status 404
         :body   (str "Corpus '" code "' not found.")})))

  (POST "/auth" [mail password]
    (let [user_data (first (kdb/with-db core-db (select user (fields :id :password) (where {:mail mail :password [not= "SAML"]}))))]
      (if (hashers/check password (:password user_data))
        (let [session_id (reduce str (take 64 (repeatedly #(rand-nth (map char (range (int \a) (inc (int \z))))))))]
          (kdb/with-db core-db
            (delete session (where (raw "expire_time < NOW()")))
            (insert session (values {:id session_id :user_id (:id user_data) :expire_time (raw (str "DATE_ADD(NOW(), INTERVAL " max-session-age " SECOND)"))})))
          {:status 200
           :cookies {"session_id" {:value session_id}}
           :max-age max-session-age})
        {:status 403
         :body (str "Wrong username or password.")})))

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

  (POST "/num-texts" [selected-metadata-ids]
    (transit-response (num-selected-texts selected-metadata-ids)))

  (POST "/num-tokens" [corpus-id queries selected-metadata-ids]
    (let [corpus (get-corpus {:id corpus-id})]
      (transit-response (token-count-matching-metadata corpus queries selected-metadata-ids)))))

(defroutes search-routes
  (POST "/search" [corpus-id search-id queries metadata-ids step page-size last-count
                   context-size sort-key]
    (transit-response (search-corpus corpus-id search-id queries metadata-ids
                                     step page-size last-count context-size sort-key) false))

  (GET "/results" [corpus-id search-id start end cpu-counts context-size sort-key]
    (transit-response (results corpus-id search-id start end cpu-counts context-size sort-key) false))

  (GET "/result-metadata" [corpus-id text-id]
    (transit-response (result-metadata (Integer/parseInt corpus-id) text-id) false))

  (GET "/play-video" [corpus-id search-id result-index context-size]
    (transit-response (play-video corpus-id search-id result-index context-size) false))

  (POST "/geo-distr" [corpus-id search-id metadata-ids]
    (transit-response (geo-distr corpus-id search-id metadata-ids) false))

  (POST "/download-results" [corpus-id search-id cpu-counts format headers? attrs context-size]
    (download-results corpus-id search-id cpu-counts format headers? attrs context-size)))
