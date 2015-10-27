(ns cglossa.db
  (:require [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [com.tinkerpop.blueprints.impls.orient OrientGraphFactory]
           [com.orientechnologies.orient.core.sql OCommandSQL]
           [com.orientechnologies.orient.core.db.record OIdentifiable]))

(defonce graph-factory (OrientGraphFactory. "remote:localhost/Glossa" "admin" "admin"))

(defn get-graph
  ([] (get-graph true))
  ([transactional?] (if transactional? (.getTx graph-factory) (.getNoTx graph-factory))))

(defn db-record? [o]
  (instance? OIdentifiable o))

(defn valid-rid? [rid]
  (re-matches #"#\d+:\d+" rid))

(defn stringify-rid [record]
  "Returns the ID of record as a string."
  (.. record getIdentity toString))

(defn- xform-val [val]
  "Transform the values of vertex properties retrieved via SQL as needed."
  (cond
    ;; When we ask for @rid in an SQL query, the value we get for the 'rid'
    ;; property is actually the entire record object. Convert it into
    ;; the string representation of the record's @rid (e.g. 12:0).
    (db-record? val) (stringify-rid val)
    (instance? Iterable val) (map xform-val val)
    :else val))

(defn vertex->map [v]
  (as->
    ;; Get all the key-value pairs for the OrientVertex
    (.getProperties v) $
    ;; Convert them to a Clojure hash map
    (into {} $)
    ;; Transform values as needed
    (walk/walk (fn [[k v]]
                 ;; Remove @ from the beginning of attributes like @rid and @class since
                 ;; keyword literals starting with a @ (such as :@rid) are invalid in Clojure
                 ;; (calling (keyword "@rid") is possible but inconvenient)
                 (let [k* (str/replace-first k #"^@" "")
                       v* (xform-val v)]
                   [k* v*])) identity $)
    (walk/keywordize-keys $)))

(defn run-sql
  ([sql]
   (run-sql sql []))
  ([sql sql-params]
   (let [graph  (get-graph)
         cmd    (OCommandSQL. sql)
         params (into-array sql-params)]
     (.. graph (command cmd) (execute params)))))

(defn sql-query
  "Takes an SQL query and optionally a map of parameters, runs it against the
  OrientDB database, and returns the query result as a lazy seq of hash maps.
  The params argument is a hash map with the following optional keys:

  * target or targets: A vertex ID, or a sequence of such IDs, to use as the target
  in the SQL query (e.g. '#12:1' or ['#12:1' '#12:2']), replacing the placeholder
  #TARGET or #TARGETS (e.g. 'select from #TARGETS').

  * strings: Map with strings that will be interpolated into the SQL query, replacing
  placeholders with the same name preceded by an ampersand. Restricted to
  letters, numbers and underscore to avoid SQL injection. Use only in those
  cases where OrientDB does not support 'real' SQL parameters (such as in the
  specification of attribute values on edges and vertices, e.g. in()[name = &name],
  with {:name 'John'} given as the value of the strings key).

  * sql-params: sequence of parameters (positioned or named) that will replace question marks
  in the SQL query through OrientDB's normal parameterization process.

  A full example:
  (sql-query \"select out('&out') from #TARGETS where code = ?\"
             {:targets    [\"#12:0\" \"#12:1\"]
              :sql-params [\"bokmal\"]
              :strings    {:out \"HasMetadataCategory\"}})"
  ([sql]
   (sql-query sql {}))
  ([sql params]
   (let [t          (or (:target params) (:targets params))
         targets    (if t (flatten [t]) [])
         _          (doseq [target targets] (assert (valid-rid? target)
                                                    (str "Invalid target: " target)))
         strings    (into {} (map (fn [[k v]] [k (.toString v)]) (:strings params)))
         _          (assert (or (nil? strings) (map? strings))
                            "String params should be provided in a map")
         _          (doseq [s (vals strings)] (assert (not (re-find #"\W" s))
                                                      (str "Invalid string param: " s)))
         sql-params (:sql-params params)
         sql*       (-> sql
                        (str/replace #"\&(\w+)" #(get strings (keyword (second %))))
                        (str/replace #"#TARGETS?" (str "[" (str/join ", " targets) "]")))
         results    (run-sql sql* sql-params)]
     (map vertex->map results))))

(defn- vertex-name [name code]
  "Creates a human-friendly name from code unless name already exists."
  (or name (-> code str/capitalize (str/replace "_" " "))))

(defn get-corpus [code]
  (let [ress (sql-query (str "SELECT @rid as corpus_rid, name as corpus_name, "
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

(def ^:private metadata-pagesize 100)

(defn unconstrained-metadata-values [category-id skip limit]
  (sql-query
    (str "SELECT @rid AS id, value AS text FROM "
         "(SELECT EXPAND(out('HasMetadataValue')) FROM #TARGET "
         "ORDER BY value SKIP &skip LIMIT &limit)")
    {:target  category-id
     :strings {:skip skip :limit limit}}))

(defn constrained-metadata-values [initial-value category-id skip limit]
  (let [corpus-cat (-> (sql-query "SELECT corpus_cat FROM #TARGET" {:target category-id})
                       first
                       :corpus_cat)]
    (sql-query
      (str "SELECT @rid AS id, value AS text FROM "
           "(SELECT EXPAND(out('DescribesText').in('DescribesText')[corpus_cat = '&category']) "
           "FROM #TARGET ORDER BY value SKIP &skip LIMIT &limit)")
      {:target  initial-value
       :strings {:category corpus-cat :skip skip :limit limit}})))

(defn get-metadata-values [category-id selected-ids page]
  (let [total (:total (first (sql-query (str "SELECT out('HasMetadataValue').size() AS total "
                                             "FROM #TARGET")
                                        {:target category-id})))
        skip  (* (dec page) metadata-pagesize)
        limit (+ skip metadata-pagesize)
        res   (if selected-ids
                (constrained-metadata-values (first (vals selected-ids)) category-id skip limit)
                (unconstrained-metadata-values category-id skip limit))
        more? (> total limit)]
    {:results (map #(select-keys % [:id :text]) res)
     :more?   more?}))
