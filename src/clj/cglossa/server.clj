(ns cglossa.server
  (:require [compojure.core :refer [routes]]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [clojure.tools.logging :as log]
            [ring.logger :refer [wrap-with-logger]]
            [korma.db :as kdb]
            [korma.core :refer [select fields]]
            [cglossa.shared :refer [corpus-connections core-db]]
            [cglossa.routes :refer [app-routes db-routes search-routes]]
            [cglossa.db.corpus :refer [corpus]]
            [cglossa.search-engines])
  (:gen-class))

(defn- init-corpus-connections! [connections]
  (assert (contains? env :glossa-db-password)
          (str "Please set the DB password for connecting to Glossa databases in the "
               "GLOSSA_DB_PASSWORD environment variable before starting the application."))
  (reset! connections
          (into {} (for [c (select corpus (fields :id :code))]
                     [(:id c)
                      (kdb/create-db (kdb/mysql {:user     (:glossa-db-user env "glossa")
                                                 :password (:glossa-db-password env)
                                                 :db       (str (get env :glossa-prefix "glossa")
                                                                "_"
                                                                (:code c))}))]))))

;; Global exception handler. From http://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
;; Assuming require [clojure.tools.logging :as log]
(defn- set-default-exception-handler! []
  (Thread/setDefaultUncaughtExceptionHandler
    (reify Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread ex]
        (log/error ex "Uncaught exception on" (.getName thread))))))

(defn wrap-db
  "Middleware that checks if the request contains a corpus-id key, and if so,
  sets the database for the given corpus as the default for the request. Otherwise
  the core database is used."
  [handler]
  (fn [request]
    (let [corpus-id (get-in request [:params :corpus-id])
          db        (if corpus-id
                      (get @corpus-connections (if (string? corpus-id)
                                                 (Integer/parseInt corpus-id)
                                                 corpus-id))
                      core-db)]
      (kdb/with-db db (handler request)))))

(def http-handler
  (let [r (routes #'db-routes #'search-routes #'app-routes)
        r (if (:is-dev env) (-> r reload/wrap-reload) r)]
    (-> r
        wrap-with-logger
        wrap-db
        wrap-keyword-params
        wrap-json-params
        wrap-params)))

(defn run [& [port]]
  (set-default-exception-handler!)
  (init-corpus-connections! corpus-connections)
  (defonce ^:private server
    (do
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-server http-handler {:port  port
                                  :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
