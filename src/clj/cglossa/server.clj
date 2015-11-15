(ns cglossa.server
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate html-content]]
            [ring.util.response :as response]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.handler.dump :refer [handle-dump]]
            [prone.middleware :refer [wrap-exceptions]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [cheshire.core :as cheshire]
            [korma.db :as kdb]
            [korma.core :refer [defentity select fields belongs-to]]
            [cglossa.db.corpus :refer [corpus get-corpus]]
            [cglossa.db.metadata :refer [get-metadata-categories]]
            [cglossa.search.core :as search]
            [cglossa.search_engines])
  (:import [java.io ByteArrayOutputStream])
  (:gen-class))

(defn hyphenize-keys
  "Recursively changes underscore to hyphen in all map keys, which are assumed
   to be strings or keywords."
  [m]
  (let [f (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v])]
    (walk/postwalk #(if (map? %) (into {} (map f %)) %) m)))

(kdb/defdb glossa-core (kdb/mysql {:user     (:db-user env)
                                   :password (:db-password env)
                                   :db       "glossa__core"}))

(def corpus-connections
  (into {} (for [corpus (select corpus (fields :code))]
             [(keyword (:code corpus))
              (kdb/create-db (kdb/mysql {:user     (:db-user env)
                                         :password (:db-password env)
                                         :db       (str "glossa_" (:code corpus))}))])))

;; Global exception handler. From http://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
;; Assuming require [clojure.tools.logging :as log]
(defn- set-default-exception-handler! []
  (Thread/setDefaultUncaughtExceptionHandler
    (reify Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread ex]
        (log/error ex "Uncaught exception on" (.getName thread))))))

(defn- transit-response* [body]
  (let [baos   (ByteArrayOutputStream. 2000)
        writer (transit/writer baos (if (:is-dev env) :json-verbose :json))
        _      (transit/write writer (hyphenize-keys body))
        res    (.toString baos)]
    (.reset baos)
    (-> (response/response res)
        (response/content-type "application/transit+json")
        (response/charset "utf-8"))))

(defmacro transit-response [fn-call]
  `(try (let [res# ~fn-call]
          (transit-response* res#))
        (catch Exception e#
          (log/error e#)
          {:status 500
           :body   (.toString e#)})))

(deftemplate page (io/resource "index.html") [])
(deftemplate admin (io/resource "admin.html")
             []
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
    (transit-response {:corpus              (get-corpus code glossa-core)
                       :metadata-categories (get-metadata-categories (get corpus-connections
                                                                          (keyword code)))}))
  (POST "/corpus" [zipfile]
    (println zipfile))
  #_(GET "/metadata-values" [category-id value-filter selected-ids page]
    (let [selected-ids* (when selected-ids (cheshire/parse-string selected-ids))
          page*         (if page (Integer/parseInt page) 1)
          data          (get-metadata-values category-id value-filter selected-ids* page*)]
      (-> (response/response (cheshire/generate-string {:results    (:results data)
                                                        :pagination {:more (:more? data)}}))
          (response/content-type "application/json")
          (response/charset "utf-8")))))

(defroutes search-routes
  (POST "/search" [corpus-id search-id queries metadata-ids step cut sort-by]
    (transit-response (search/search corpus-id search-id queries metadata-ids step cut sort-by)))
  (GET "/results" [search-id start end sort-by]
    (transit-response (search/results search-id start end sort-by))))

(def http-handler
  (let [r (routes #'db-routes #'search-routes #'app-routes)
        r (if (:is-dev env) (-> r reload/wrap-reload wrap-exceptions) r)]
    (-> r
        wrap-keyword-params
        wrap-json-params
        wrap-params)))

(defn run [& [port]]
  (set-default-exception-handler!)
  (defonce ^:private server
           (do
             (let [port (Integer. (or port (env :port) 10555))]
               (print "Starting web server on port" port ".\n")
               (run-server http-handler {:port  port
                                         :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
