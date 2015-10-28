(ns cglossa.db.shared
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

(defn vertex-name [name code]
  "Creates a human-friendly name from code unless name already exists."
  (or name (-> code str/capitalize (str/replace "_" " "))))

(defn build-sql [sql params]
  (let [t       (or (:target params) (:targets params))
        ;_ (println t)
        targets (if t
                  (if (set? t)
                    t
                    (flatten [t]))
                  [])
        _       (doseq [target targets] (assert (valid-rid? target)
                                                (str "Invalid target: " target)))
        strings (into {} (map (fn [[k v]] [k (.toString v)]) (:strings params)))
        _       (assert (or (nil? strings) (map? strings))
                        "String params should be provided in a map")
        _       (doseq [s (vals strings)] (assert (not (re-find #"\W" s))
                                                  (str "Invalid string param: " s)))]
    (-> sql
        (str/replace #"\&(\w+)" #(get strings (keyword (second %))))
        (str/replace #"#TARGETS?" (str "[" (str/join ", " targets) "]")))))

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
   (let [sql*       (build-sql sql params)
         sql-params (:sql-params params)
         results    (run-sql sql* sql-params)]
     (map vertex->map results))))
