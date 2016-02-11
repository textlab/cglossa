(ns cglossa.db.corpus
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity transform select where]]
            [clojure.edn :as edn]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defentity corpus
  (transform (fn [{:keys [languages] :as c}]
               (assoc c :languages (edn/read-string languages)))))

(defn- merge-language-info [c]
  (let [taggers   (->> c :languages (map :tagger))
        tags      (map (fn [tagger]
                         (when tagger
                           (edn/read-string (slurp (str "resources/taggers/" tagger ".edn")))))
                       taggers)
        ;; If the first element in the seq read from the tagger file is a hash map, it should
        ;; be a config map (e.g. specifying the name of the part-of-speech attribute if it
        ;; deviates from the default 'pos'). The rest should be seqs containing descriptions
        ;; of parts-of-speech and their morphosyntactic features.
        config    (map #(if (map? (first %)) (first %) {}) tags)
        menu-data (map #(if (map? (first %)) (next %) %) tags)
        languages (map #(assoc %1 :config %2 :menu-data %3) (:languages c) config menu-data)]
    (assoc c :languages languages)))

(defn get-corpus [conditions]
  (kdb/with-db core-db
    (-> (select corpus (where conditions))
        first
        merge-language-info)))

(defn multilingual? [corpus]
  (> (-> corpus :languages count) 1))
