(ns cglossa.server
  (:require [compojure.core :refer [routes]]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :refer [rotor-appender]]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [korma.db :as kdb]
            [korma.core :refer [defentity table select insert delete values fields where raw join]]
            [cglossa.shared :refer [corpus-connections mysql core-db]]
            [cglossa.routes :refer [app-routes db-routes search-routes corpus-home]]
            [cglossa.db.corpus :refer [corpus]]
            [cglossa.search-engines])
  (:gen-class))

(use 'ring.middleware.cookies)

(defn- init-corpus-connections! [connections]
  (assert (contains? env :glossa-db-password)
          (str "Please set the DB password for connecting to Glossa databases in the "
               "GLOSSA_DB_PASSWORD environment variable before starting the application."))
  (reset! connections
          (into {} (for [c (select corpus (fields :code))]
                     [(:code c)
                      (kdb/create-db (mysql {:user              (:glossa-db-user env "glossa")
                                             :password          (:glossa-db-password env)
                                             :useUnicode        true
                                             :characterEncoding "UTF-8"
                                             :db                (str (get env :glossa-prefix "glossa")
                                                                     "_"
                                                                     (:code c))}))]))))
(defn wrap-db
  "Middleware that checks if the url starts with a valid corpus code, and if so,
  sets the database for the given corpus as the default for the request. Otherwise
  the core database is used."
  [handler]
  (fn [request]
    (let [corpus-code (->> request :uri (re-find #"^/?(.+?)/") second)
          db          (if (and corpus-code (contains? @corpus-connections corpus-code))
                        (get @corpus-connections corpus-code)
                        core-db)]
      (kdb/with-db db (handler request)))))

(defentity session (table :session))
(defentity user (table :user))
(defn wrap-auth [handler]
  (fn [request]
    (if-let [user-data (and (re-find #"^/[^/]+/corpus$" (:uri request))
                            (if-let [session_id (:value (get (:cookies request) "session_id"))]
                              (first (kdb/with-db core-db (select session (join user (= :session.user_id :user.id))
                                                                  (fields :user.id :user.mail :user.eduPersonPrincipalName :user.displayName)
                                                                  (where {:session.id session_id})
                                                                  (where (raw "session.expire_time >= NOW()")))))))]
      (handler (assoc request :user-data user-data))
      (handler request))))

(def http-handler
  ;; NOTE: Make sure corpus-home is the last attempted match, since it does not specify anything
  ;; other than the fact that the URL only contains one part
  (let [r (routes #'db-routes #'search-routes #'app-routes #'corpus-home)
        r (if (:is-dev env) (reload/wrap-reload r) r)]
    (-> r
        wrap-with-logger
        wrap-db
        wrap-keyword-params
        wrap-json-params
        wrap-auth
        wrap-cookies
        wrap-params)))

(defn run [& [port]]
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/merge-config! {:appenders
                         {:rotor (rotor-appender {:path (str "./log/timbre-rotor."
                                                             (if (:is-dev env) "dev" "prod")
                                                             ".log")})}})
  (init-corpus-connections! corpus-connections)
  (defonce ^:private server
    (let [port (Integer. (or port (env :port) 10555))]
      (print "Starting web server on port" port ".\n")
      (run-server http-handler {:port     port
                                :max-line 8192
                                :join?    false})))
  server)

(defn -main [& [port]]
  (run port))
