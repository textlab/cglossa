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
            [cglossa.search.core :refer [search-corpus stats-corpus results geo-distr download-results]]
            [cglossa.db.corpus :refer [corpus]]
            [cglossa.search.cwb.speech :refer [play-video]])
  (:import (java.io ByteArrayOutputStream)))

(def max-session-age 86400)             ; in seconds
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
                              [:li :a] (set-attr :href (str (:code corp) "/home"))))

(deftemplate admin (io/resource "admin.html") []
  [:#corpus-table]
  (html-content
    (str "<tr><td style=\"width: 350px;\">AA</td>"
         "<td><button class=\"btn btn-danger btn-xs\">Delete</td></tr>")))

(defroutes app-routes
  (files "/" {:root "resources/public" :mime-types {"tsv" "text/tab-separated-values"}})
  (files "/:corpus-code/" {:root "resources/public" :mime-types {"tsv" "text/tab-separated-values"}})
  (resources "/" {:mime-types {"tsv" "text/tab-separated-values"}})
  (resources "/:corpus-code/" {:mime-types {"tsv" "text/tab-separated-values"}})
  (GET "/:corpus-code/request" [] handle-dump)
  ;(GET "/admin" req (admin))
  (GET "/" [] (front))
  (GET "/:corpus-code/home" [] (page)))

(defroutes db-routes
  (GET "/:corpus-code/corpus" [corpus-code :as {user-data :user-data}]
    (let [c (get-corpus {:code corpus-code})]
      (if (:id c)
        (let [cats (kdb/with-db (get @corpus-connections corpus-code) (get-metadata-categories))]
          (transit-response {:corpus              c
                             :metadata-categories cats
                             :authenticated-user  (or (:displayName user-data) (:mail user-data))}))
        {:status 404
         :body   (str "Corpus '" corpus-code "' not found.")})))

  (POST "/:corpus-code/auth" [mail password]
    (let [user_data (first (kdb/with-db core-db (select user (fields :id :password) (where {:mail mail :password [not= "SAML"]}))))]
      (if (hashers/check password (:password user_data))
        (let [session_id (reduce str (take 64 (repeatedly #(rand-nth (map char (range (int \a) (inc (int \z))))))))]
          (kdb/with-db core-db
            (delete session (where (raw "expire_time < NOW()")))
            (insert session (values {:id session_id :user_id (:id user_data) :expire_time (raw (str "DATE_ADD(NOW(), INTERVAL " max-session-age " SECOND)"))})))
          {:status  200
           :cookies {"session_id" {:value session_id}}
           :max-age max-session-age})
        {:status 403
         :body   (str "Wrong username or password.")})))

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

  (POST "/:corpus-code/texts" [selected-metadata ncats page]
    (let [ncats* (or ncats 1)
          page*  (or page 1)]
      (transit-response (show-texts selected-metadata ncats* page*) false)))

  (POST "/:corpus-code/num-texts" [selected-metadata-ids]
    (transit-response (num-selected-texts selected-metadata-ids)))

  (POST "/:corpus-code/num-tokens" [corpus-code queries selected-metadata-ids]
    (let [corpus (get-corpus {:code corpus-code})]
      (transit-response (token-count-matching-metadata corpus queries selected-metadata-ids)))))

(defroutes search-routes
  (POST "/:corpus-code/search" [corpus-code search-id queries metadata-ids step page-size last-count
                                context-size sort-key]
    (transit-response (search-corpus corpus-code search-id queries metadata-ids
                                     step page-size last-count context-size sort-key) false))

  (POST "/:corpus-code/stats" [corpus-code search-id queries metadata-ids step page-size last-count
                               context-size sort-key freq-attr]
    (transit-response (stats-corpus corpus-code search-id queries metadata-ids
                                    step page-size last-count context-size sort-key freq-attr)
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
                                          attrs context-size]
    (download-results corpus-code search-id cpu-counts format headers? attrs context-size)))
