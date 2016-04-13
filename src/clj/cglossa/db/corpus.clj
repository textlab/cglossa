(ns cglossa.db.corpus
  (:require [korma.db :as kdb]
            [korma.core :refer [defentity transform select where]]
            [clojure.edn :as edn]
            [me.raynes.conch :as conch]
            [me.raynes.fs :as fs]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]))

(defn multilingual? [corpus]
  (> (-> corpus :languages count) 1))


(defmulti extra-info
  "Allows additional info besides the one residing in the database to be
  gathered using a procedure determined by the corpus type."
  (fn [corpus] (:search-engine corpus)))


(defmethod extra-info :default [c]
  (conch/with-programs [cwb-describe-corpus]
    (let [cwb-corpora (if (multilingual? c)
                        (map (fn [lang]
                               (str (:code c) "_" (name (:code lang))))
                             (:languages c))
                        [(:code c)])
          sizes       (reduce (fn [m cwb-corpus]
                                (let [corpus-descr (cwb-describe-corpus cwb-corpus {:seq true})
                                      size-line    (first (filter #(re-find #"^size\s+\(tokens\)" %)
                                                                  corpus-descr))
                                      size         (->> size-line
                                                        (re-find #"^size\s+\(tokens\):\s+(\d+)")
                                                        second
                                                        (Integer/parseInt))]
                                  (assoc m cwb-corpus size)))
                              {}
                              cwb-corpora)]
      {:size sizes})))


(defentity corpus
  (transform (fn [{:keys [languages] :as c}]
               ;; Don't do the extra transformations if we have only requested a few specific
               ;; fields, excluding languages
               (if languages
                 (as-> c $
                       (assoc $ :languages (edn/read-string languages))
                       (assoc $ :extra-info (extra-info $))
                       (assoc $ :audio? (fs/exists? (str "resources/public/media/"
                                                         (:code $) "/audio")))
                       (assoc $ :video? (fs/exists? (str "resources/public/media/"
                                                         (:code $) "/video")))
                       (assoc $ :geo-coord (let [path (str "resources/geo_coord/"
                                                           (:code $) ".edn")]
                                             (when (fs/exists? path)
                                               (edn/read-string (slurp path))))))
                 c))))


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
