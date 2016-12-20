(require '[buddy.hashers :as hashers]
         '[korma.core :refer [defentity table insert values]]
         '[korma.db :as kdb]
         '[cglossa.shared :refer [core-db]])
(let [[mail displayName password] (rest *command-line-args*)]
  (kdb/with-db core-db
    (defentity user (table :user))
    (insert user (values {:mail mail :displayName displayName :password (hashers/derive password)}))))
