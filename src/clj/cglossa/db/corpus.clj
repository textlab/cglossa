(ns cglossa.db.corpus
  (:require [korma.db :refer [with-db]]
            [korma.core :refer [defentity select where]]
            [cglossa.db.metadata :refer [metadata_category]]))

(defentity corpus)

;;;; DUMMIES
(defn build-sql [_ _])
(defn sql-query [_ _])
(defn vertex-name [_])

(defn get-corpus [code db]
  (with-db db
    (first (select corpus (where {:code code}))))
  #_(let [ress (sql-query (str "SELECT @rid as corpus_rid, name as corpus_name, "
                               "logo, search_engine, has_phonetic, has_headword_search, "
                               "$cats.@rid as cat_rids, $cats.code as cat_codes, "
                               "$cats.name as cat_names "
                               "FROM Corpus "
                               "LET $cats = out('HasMetadataCategory') "
                               "WHERE code = ?")
                          {:sql-params [code]})
          res  (as-> ress $
                     (first $)
                     (update $ :cat_names (partial map vertex-name) (:cat_codes $)))]
      {:corpus              {:rid                 (:corpus_rid res)
                             :name                (:corpus_name res)
                             :logo                (:logo res)
                             :search-engine       (:search_engine res :cwb)
                             :has-phonetic        (:has_phonetic res)
                             :has-headword-search (:has_headword_search res)}
       :metadata-categories (-> (map (fn [rid name] {:rid rid :name name})
                                     (:cat_rids res)
                                     (:cat_names res)))}))
