(ns cglossa.db.corpus
  (:require [clojure.string :as str]
            [korma.db :as kdb]
            [korma.core :refer [defentity transform select where set-fields]]
            [clojure.edn :as edn]
            [me.raynes.conch :as conch]
            [me.raynes.fs :as fs]
            [cglossa.shared :refer [core-db]]
            [cglossa.db.metadata :refer [metadata-category]]
            [korma.core :as korma]))

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


(defn calc-multicore-bounds [c]
  "Calculates the upper boundaries (i.e., corpus positions) for each corpus
   part to be searched by a separate thread in a multicore machine."
  (let [corpus-size (get-in c [:extra-info :size (:code c)])
        ncores      (.availableProcessors (Runtime/getRuntime))
        ;; Calculate the block size for each core by dividing into equal blocks the corpus sizes
        ;; searched in each search step (first 5 mill words, then the following 45 mill words,
        ;; and then the whole corpus)
        block-sizes (for [total [5000000 45000000 (- corpus-size 50000000)]]
                      (int (Math/ceil (/ (float total) (float ncores)))))
        block-ends  [(map #(* (nth block-sizes 0) %) (range 1 (inc ncores)))
                     (map #(+ 5000000 (* (nth block-sizes 1) %)) (range 1 (inc ncores)))
                     (map #(+ 50000000 (* (nth block-sizes 2) %)) (range 1 (inc ncores)))]
        text-ends   (conch/with-programs [cwb-s-decode]
                      (->> (cwb-s-decode (:code c) "-S" "text" {:seq true})
                           (map #(str/split % #"\t"))
                           (map last)
                           (map #(Integer/parseInt %))))]
    (mapv (fn [step-block-ends]
            (mapv (fn [block-end]
                    (let [smaller (take-while #(< % block-end) text-ends)]
                      (last smaller)))
                  step-block-ends))
          block-ends)))


(declare corpus)
(defn- set-multicore-bounds [c bounds]
  (korma/update corpus
                (set-fields {:multicore_bounds (pr-str bounds)})
                (where {:code (:code c)})))


(defentity corpus
  (transform (fn [{:keys [languages multicore_bounds] :as c}]
               ;; Don't do the extra transformations if we have only requested a few specific
               ;; fields, excluding languages
               (if languages
                 (let [c*     (as-> c $
                                    (assoc $ :languages (edn/read-string languages))
                                    (assoc $ :extra-info (extra-info $))
                                    (assoc $ :multicore_bounds (edn/read-string multicore_bounds))
                                    (assoc $ :audio? (fs/exists? (str "resources/public/media/"
                                                                      (:code $) "/audio")))
                                    (assoc $ :video? (fs/exists? (str "resources/public/media/"
                                                                      (:code $) "/video")))
                                    (assoc $ :geo-coord (let [path (str "resources/geo_coord/"
                                                                        (:code $) ".edn")]
                                                          (when (fs/exists? path)
                                                            (edn/read-string (slurp path))))))
                       mb     (:multicore_bounds c*)
                       ncores (.availableProcessors (Runtime/getRuntime))]
                   (if (and
                         ;; This corpus should use multicore processing...
                         mb
                         (or
                           ;; ...but we have just marked that we want it, not actually calculated
                           ;; the bounds...
                           (not (sequential? mb))
                           ;; ...or the number of parts is different from the number of cores,
                           ;; indicating that the corpus has been copied from a machine with a
                           ;; different number of cores...
                           (not= (count (first mb)) ncores)
                           ;; ...or the last bound is different from the corpus size, indicating
                           ;; that the contents of the corpus have changed since we calculated the
                           ;; bounds...
                           (not= (last (last mb))
                                 (dec (get-in c* [:extra-info :size (:code c*)])))))
                     ;; ...so calculate new bounds and store them
                     (let [mb* (calc-multicore-bounds c*)]
                       (set-multicore-bounds c* mb*)
                       (assoc c* :multicore_bounds mb*))
                     c*))
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
