(ns cglossa.search.cwb.shared
  "Shared code for all types of corpora encoded with the IMS Open Corpus Workbench."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [korma.core :refer [defentity select select* modifier fields join where raw]]
            [me.raynes.fs :as fs]
            [me.raynes.conch.low-level :as sh]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [cglossa.shared :as shared]
            [cglossa.db.corpus :refer [multilingual?]]
            [cglossa.db.metadata :refer [metadata-value-text]])
  (:import [java.sql SQLException]))

(defentity text)

(defn cwb-corpus-name [corpus queries]
  (let [uc-code (str/upper-case (:code corpus))]
    (if (multilingual? corpus)
      ;; The CWB corpus we select before running our query will be the one named by the
      ;; code attribute of the corpus plus the name of the language of the first
      ;; submitted query row (e.g. RUN_EN).
      (str uc-code "_" (-> queries first :lang str/upper-case))
      uc-code)))

(defn cwb-query-name
  "Constructs a name for the saved query in CQP, e.g. MYCORPUS11."
  [corpus search-id]
  (str (str/upper-case (:code corpus)) search-id))

(defn- build-monolingual-query [queries s-tag]
  ;; For monolingual queries, the query expressions should be joined together with '|' (i.e., "or")
  (let [queries*  (map :query queries)
        query-str (if (> (count queries*) 1)
                    (str/join " | " queries*)
                    (first queries*))]
    (str query-str " within " s-tag)))

(defn- build-multilingual-query [corpus queries s-tag]
  (let [corpus-code     (-> corpus :code str/upper-case)
        main-query      (str (-> queries first :query) " within " s-tag)
        aligned-queries (for [query (rest queries)
                              ;; TODO: In case of mandatory alignment, include even empty queries
                              :when (not (str/blank? (:query query)))]
                          (str corpus-code "_" (-> query :lang str/upper-case) " " (:query query)))]
    (str/join " :" (cons main-query aligned-queries))))

(defmulti position-fields
  "The database fields that contain corpus positions for texts."
  (fn [corpus _] (:search_engine corpus)))

(defmulti order-position-fields
  "Ordering of the database fields that contain corpus positions for texts."
  (fn [_ corpus] (:search_engine corpus)))

(defn- join-metadata
  "Adds a join with the metadata_value_text table for each metadata category
  for which we have selected one or more values."
  [sql metadata-ids]
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

(defn- where-metadata
  "For each metadata category for which we have selected one or more
  values, adds a 'where' clause with the ids of the metadata values in that
  category. The 'where' clause is associated with the corresponding instance of
  the metadata_value_text table that is joined in by the join-metadata
  function.

  This gives us an OR (union) relationship between values from the same
  category and an AND (intersection) relationship between values from different
  categories."
  [sql metadata-ids]
  (let [cats (map-indexed (fn [index [_ ids]] [index ids]) metadata-ids)]
    (reduce (fn [q [cat-index cat-ids]]
              (let [alias     (str \j (inc cat-index))
                    fieldname (keyword (str alias ".metadata_value_id"))]
                (where q {fieldname [in cat-ids]})))
            sql
            cats)))

(defn where-language [sql corpus queries]
  (if (multilingual? corpus)
    (where sql {:language (-> queries first :lang)})
    sql))

(defmulti where-limits
  "Limits selected corpus positions to be between the supplied start and end positions,
  if applicable to the current corpus type."
  (fn [_ corpus _ _] (:search-engine corpus)))

;; The default implementation of where-limits does nothing.
(defmethod where-limits :default [sql _ _ _] sql)

(defn- print-positions-matching-metadata
  "Prints to file the start and stop positions of all corpus texts that are
  associated with the metadata values that have the given database ids, with an
  OR relationship between values within the same category and an AND
  relationship between categories. Also restricts the positions to the start
  and end positions provided in the request."
  [corpus queries metadata-ids startpos endpos positions-filename]
  ;; It seems impossible to prevent Korma (or rather the underlying Java library)
  ;; from throwing an exception when we do a SELECT that does not return any results
  ;; because they are written to file instead using INTO OUTFILE. However, the
  ;; results are written to the file just fine despite the exception (which happens
  ;; after the query has run), so we can just catch and ignore the exception.
  (fs/delete positions-filename)
  (if (seq metadata-ids)
    (try
      (-> (select* [text :t])
          (modifier "DISTINCT")
          (fields (position-fields corpus positions-filename))
          (join-metadata metadata-ids)
          (where-metadata metadata-ids)
          (where-language corpus queries)
          (where-limits corpus startpos endpos)
          (order-position-fields corpus)
          (select))
      (catch SQLException e
        (when-not (.contains (str e) "ResultSet is from UPDATE")
          (println e))))
    ;; No metadata selected, so just print the start and end positions specified in the
    ;; request, making sure that the end position does not exceed the size of the corpus.
    (let [sizes       (get-in corpus [:extra-info :size])
          corpus-name (str/lower-case (cwb-corpus-name corpus queries))
          corpus-size (get sizes corpus-name)
          endpos*     (if endpos
                        (min endpos (dec corpus-size))
                        (dec corpus-size))]
      (spit positions-filename (str startpos \tab endpos* \newline)))))

(defn displayed-attrs-command [corpus queries attrs]
  ;; NOTE: CWB doesn't seem to allow different attributes to be specified for each aligned
  ;; query(?), so for now at least we just ask for the attributes of the tagger used for
  ;; the first query
  (if attrs
    (str "show -word; show " (str/join " " (map #(str "+" (name %)) attrs)))
    (let [first-query-lang      (-> queries first :lang)
          corpus-lang           (->> corpus
                                     :languages
                                     (filter #(= (:code %) first-query-lang))
                                     first)
          displayed-attrs       (->> corpus-lang :config :displayed-attrs (map first))
          corpus-specific-attrs (->> corpus-lang
                                     :corpus-specific-attrs
                                     (map first))]
      (when (seq displayed-attrs)
        (str "show " (str/join " " (map #(str "+" (name %)) (concat displayed-attrs
                                                                    corpus-specific-attrs))))))))

(defn aligned-languages-command [corpus queries]
  (let [lang-codes           (map :lang queries)
        first-lang-code      (first lang-codes)
        non-first-lang-codes (-> lang-codes set (disj first-lang-code))]
    ;; Only show alignment attributes if we have actually asked for aligned languages
    (when (> (count lang-codes) 1)
      (str "show " (str/join " " (map #(str "+" (:code corpus) "_" %) non-first-lang-codes))))))

(defn sort-command [named-query sort-key]
  (when-let [context (case sort-key
                       "position" nil
                       "match" ""
                       "left-immediate" " on match[-1]"
                       "left-wide" " on match[-1] .. match[-10]"
                       "right-immediate" " on matchend[1]"
                       "right-wide" " on matchend[1] .. matchend[10]")]
    ["set ExternalSort on"
     (str "sort " named-query " by word %c" context)]))

(defn construct-query-commands [corpus queries metadata-ids named-query search-id startpos endpos
                                & {:keys [s-tag cpu-index] :or {s-tag "s"}}]
  (let [query-str          (if (multilingual? corpus)
                             (build-multilingual-query corpus queries s-tag)
                             (build-monolingual-query queries s-tag))
        positions-filename (str (fs/tmpdir) "/glossa/positions_" search-id (when cpu-index
                                                                             (str "_" cpu-index)))
        init-cmds          [(str "undump " named-query " < '" positions-filename \') named-query]]
    (print-positions-matching-metadata corpus queries metadata-ids startpos endpos
                                       positions-filename)
    (conj init-cmds (str named-query " = " query-str))))

(defn run-cqp-commands [corpus commands counting?]
  (let [commands* (->> commands
                       (map #(str % \;))
                       (str/join \newline))
        encoding  (:encoding corpus "UTF-8")
        cqp       (sh/proc "cqp" "-c" :env {"LC_ALL" encoding})
        ;; Run the CQP commands and capture the output
        out       (do
                    ;; We need to use our own implementation of feed-from-string here because
                    ;; of problems with sending options to the original in me.raynes.conch.low-level
                    (shared/feed-from-string cqp commands* :encoding encoding)
                    (sh/done cqp)
                    (sh/stream-to-string cqp :out :encoding encoding))
        err       (sh/stream-to-string cqp :err)
        _         (assert (str/blank? err) (if (:is-dev env) (println err) (timbre/error err)))
        ;; Split into lines and throw away the first line, which contains the CQP version.
        ;; If counting? is true (which it is when we are searching, but not when retrieving
        ;; results), the first line after that contains the number of results. Any following
        ;; lines contain actual search results (only in the first step).
        res       (rest (str/split-lines out))
        cnt       (when counting? (first res))
        results   (if counting? (rest res) res)]
    (if (and (pos? (count results))
             (re-find #"PARSE ERROR|CQP Error" (first results)))
      (throw (str "CQP error: " results))
      [results cnt])))
