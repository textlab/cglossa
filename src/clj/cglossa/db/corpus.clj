(ns cglossa.db.corpus
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity transform select where]]
            [clojure.edn :as edn]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defentity corpus
  (transform (fn [{:keys [languages] :as c}]
               (assoc c :languages (edn/read-string languages)))))

(defn corpus-by-code [code]
  (kdb/with-db core-db
    (first (select corpus (where {:code code})))))

(defn corpus-by-id [id]
  (kdb/with-db core-db
    (first (select corpus (where {:id id})))))
