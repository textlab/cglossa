(ns cglossa.search.cwb.shared
  "Shared code for all types of corpora encoded with the IMS Open Corpus Workbench."
  (:require [korma.core :refer [defentity select select* modifier fields join where raw]]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [me.raynes.conch.low-level :as sh]
            [cglossa.db.metadata :refer [metadata-value-text]])
  (:import [java.sql SQLException]))

(defentity text)

(defn cwb-corpus-name [corpus queries]
  (let [uc-code (str/upper-case (:code corpus))]
    (if (:multilingual? corpus)
      ;; The CWB corpus we select before running our query will be the one named by the
      ;; code attribute of the corpus plus the name of the language of the first
      ;; submitted query row (e.g. RUN_EN).
      (str uc-code "_" (-> queries first :lang str/upper-case))
      uc-code)))

(defn cwb-query-name [corpus search-id]
  "Constructs a name for the saved query in CQP, e.g. MYCORPUS11."
  (str (str/upper-case (:code corpus)) search-id))

(defn- build-monolingual-query [queries s-tag]
  ;; For monolingual queries, the query expressions should be joined together with '|' (i.e., "or")
  (let [queries*  (map :query queries)
        query-str (if (> (count queries*) 1)
                    (str/join " | " queries*)
                    (first queries*))]
    (str query-str " within " s-tag)))

(defn- build-multilingual-query [queries s-tag]
  ;; TODO
  )

(defmulti position-fields
  "The database fields that contain corpus positions for texts."
  (fn [corpus _] (:search_engine corpus)))

(defn- join-metadata [sql metadata-ids]
  "Adds a join with the metadata_value_text table for each metadata category
  for which we have selected one or more values."
  (reduce (fn [q join-index]
            (let [alias1         (str \j (inc join-index))
                  alias2         (str \j join-index)
                  make-fieldname #(keyword (str % ".text_id"))]
              (join q :inner [metadata-value-text alias1]
                    (= (make-fieldname alias1) (if (zero? join-index)
                                                 :t.id
                                                 (make-fieldname alias2))))))
          sql
          (-> metadata-ids count range)))

(defn- where-metadata [sql metadata-ids]
  "For each metadata category for which we have selected one or more
  values, adds a 'where' clause with the ids of the metadata values in that
  category. The 'where' clause is associated with the corresponding instance of
  the metadata_value_text table that is joined in by the join-metadata
  function.

  This gives us an OR (union) relationship between values from the same
  category and an AND (intersection) relationship between values from different
  categories."
  (let [cats (map-indexed (fn [index [_ ids]] [index ids]) metadata-ids)]
    (reduce (fn [q [cat-index cat-ids]]
              (let [alias     (str \j (inc cat-index))
                    fieldname (keyword (str alias ".metadata_value_id"))]
                (where q {fieldname [in cat-ids]})))
            sql
            cats)))

(defn- print-positions-matching-metadata [corpus metadata-ids positions-filename]
  "Returns start and stop positions of all corpus texts that are associated
  with the metadata values that have the given database ids, with an OR
  relationship between values within the same category and an AND relationship
  between categories."
  ;; It seems impossible to prevent Korma (or rather the underlying Java library)
  ;; from throwing an exception when we do a SELECT that does not return any results
  ;; because they are written to file instead using INTO OUTFILE. However, the
  ;; results are written to the file just fine despite the exception (which happens
  ;; after the query has run), so we can just catch and ignore the exception.
  (println positions-filename)
  (try
    (-> (select* [text :t])
        (modifier "DISTINCT")
        (fields (position-fields corpus positions-filename))
        (join-metadata metadata-ids)
        (where-metadata metadata-ids)
        (select))
    (catch SQLException e
      (when-not (.contains (.toString e) "ResultSet is from UPDATE")
        (println e)))))

(defn construct-query-commands [corpus queries metadata-ids named-query search-id cut step
                                & {:keys [s-tag] :or {s-tag "s"}}]
  (let [query-str (if (:multilingual? corpus)
                    (build-multilingual-query queries s-tag)
                    (build-monolingual-query queries s-tag))
        init-cmds (if metadata-ids
                    (let [positions-filename (str (fs/tmpdir) "/positions_" search-id)]
                      (when (= step 1)
                        (print-positions-matching-metadata corpus metadata-ids positions-filename))
                      [(str "undump " named-query " < '" positions-filename \') named-query])
                    [])
        cut-str   (when cut (str " cut " cut))]
    (conj init-cmds (str named-query " = " query-str cut-str))))

(defn run-cqp-commands [corpus commands]
  (let [commands* (->> commands
                       (map #(str % \;))
                       (str/join \newline))]
    (let [cqp      (sh/proc "cqp" "-c")
          encoding (:encoding corpus "UTF-8")
          ;; Run the CQP commands and capture the output
          out      (do
                     (sh/feed-from-string cqp commands*)
                     (sh/done cqp)
                     (sh/stream-to-string cqp :out :encoding encoding))
          ;; Split into lines and throw away the first line, which contains the CQP version
          results  (rest (str/split-lines out))]
      (if (and (pos? (count results))
               (re-find #"PARSE ERROR|CQP Error" (first results)))
        (throw (str "CQP error: " results))
        results))))
