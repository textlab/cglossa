(ns cglossa.db.corpus
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity transform select where]]
            [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defentity corpus
  (transform (fn [{:keys [languages] :as c}]
               (-> c
                   (assoc :languages (edn/read-string languages))
                   (assoc :audio? (fs/exists? (str "resources/public/media/"
                                                   (:code c) "/audio")))
                   (assoc :video? (fs/exists? (str "resources/public/media/"
                                                   (:code c) "/video")))))))

(defn- merge-tagger-attrs [c]
  (let [taggers   (->> c :languages (map :tagger))
        tags      (map (fn [tagger]
                         (when tagger
                           (edn/read-string (slurp (str "resources/attributes/" tagger ".edn")))))
                       taggers)
        ;; If the first element in the seq read from the tagger file is a hash map, it should
        ;; be a config map (e.g. specifying the name of the part-of-speech attribute if it
        ;; deviates from the default 'pos'). The rest should be seqs containing descriptions
        ;; of parts-of-speech and their morphosyntactic features.
        config    (map #(if (map? (first %)) (first %) {}) tags)
        menu-data (map #(if (map? (first %)) (next %) %) tags)
        languages (map #(assoc %1 :config %2 :menu-data %3) (:languages c) config menu-data)]
    (assoc c :languages languages)))

(defn- merge-corpus-specific-attrs [c]
  (let [lang-codes (->> c :languages (map :code))
        attrs      (for [lang-code lang-codes
                         :let [path (str "resources/attributes/corpora/"
                                         (:code c) "_" (name lang-code) ".edn")]]
                     (when (fs/exists? path)
                       (edn/read-string (slurp path))))
        languages  (map #(assoc %1 :corpus-specific-attrs %2) (:languages c) attrs)]
    (assoc c :languages languages)))

(defn get-corpus [conditions]
  (kdb/with-db core-db
    (-> (select corpus (where conditions))
        first
        merge-tagger-attrs
        merge-corpus-specific-attrs)))

(defn multilingual? [corpus]
  (> (-> corpus :languages count) 1))
