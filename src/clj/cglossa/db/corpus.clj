(ns cglossa.db.corpus
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity select where]]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defentity corpus)

(defn corpus-by-code [code]
  (kdb/with-db core-db
    (first (select corpus (where {:code code})))))

(defn corpus-by-id [id]
  (kdb/with-db core-db
    (first (select corpus (where {:id id})))))
