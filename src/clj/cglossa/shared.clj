(ns cglossa.shared
  (:require [environ.core :refer [env]]
            [korma.db :as kdb]))

(defonce corpus-connections (atom {}))

(def core-db-name (str (get env :glossa-prefix "glossa") "__core"))

(kdb/defdb core-db (kdb/mysql {:user     (:glossa-db-user env)
                               :password (:glossa-db-password env)
                               :db       core-db-name}))
