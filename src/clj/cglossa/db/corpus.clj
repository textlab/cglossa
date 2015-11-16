(ns cglossa.db.corpus
  (:require [korma.core :refer [defentity select where]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defentity corpus)

(defn get-corpus [code]
  (first (select corpus (where {:code code}))))
