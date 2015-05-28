(ns cglossa.data-import.metadata-values
  (:require [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [cglossa.data-import.utils :as utils]))

(def ^:private non-tids-template
  {:begin        [{:console
                   {:commands
                    ["CONNECT remote:localhost/Glossa admin admin;"
                     "TRUNCATE CLASS MetadataValue UNSAFE;"]}}]
   :source       {:file {:path "###TSV-PATH###"}}
   :extractor    {:row {}}
   :transformers [{:csv {:separator "\t"}}
                  {:vertex {:class "MetadataValue"}}
                  {:edge
                   {:class                "InCategory"
                    :lookup               "MetadataCategory.corpus_cat"
                    :joinFieldName        "corpus_cat"
                    :unresolvedLinkAction "ERROR"}}]
   :loader       {:orientdb
                  {:dbURL               "remote:localhost/Glossa"
                   :dbType              "graph"
                   :wal                 false
                   :useLightweightEdges true
                   :classes             [{:name "MetadataCategory" :extends "V"}
                                         {:name "MetadataValue" :extends "V"}]
                   :indexes             [{:class  "MetadataCategory"
                                          :fields ["corpus_cat:string"]
                                          :type   "UNIQUE"}]}}})

(def ^:private tids-template
  {:source       {:file {:path "###TSV-PATH###"}}
   :extractor    {:row {}}
   :transformers [{:csv {:separator "\t"}}
                  {:vertex {:class "MetadataValue"}}
                  {:field {:fieldName "value" :expression "tid"}}
                  {:field {:fieldName "tid" :operation "remove"}}
                  {:edge
                   {:class                "InCategory"
                    :lookup               "MetadataCategory.corpus_cat"
                    :joinValue            "###CORPUS###_tid"
                    :unresolvedLinkAction "ERROR"}}]
   :loader       {:orientdb
                  {:dbURL               "remote:localhost/Glossa"
                   :dbType              "graph"
                   :wal                 false
                   :useLightweightEdges true
                   :classes             [{:name "MetadataCategory" :extends "V"}
                                         {:name "MetadataValue" :extends "V"}]
                   :indexes             [{:class  "MetadataCategory"
                                          :fields ["corpus_cat:string"]
                                          :type   "UNIQUE"}
                                         {:class  "MetadataValue"
                                          :fields ["corpus_cat:string" "value:string"]
                                          :type   "UNIQUE"}]}}})

(defn- create-non-tid-config! [config-path tsv-path]
  (spit config-path (-> non-tids-template
                        (cheshire/generate-string {:pretty true})
                        (str/replace "###TSV-PATH###" tsv-path))))

(defn- create-non-tid-tsv! [corpus orig-tsv-path tsv-path]
  (with-open [orig-tsv-file (io/reader orig-tsv-path)
              tsv-file      (io/writer tsv-path)]
    (let [[headers & rows] (utils/read-csv orig-tsv-file)
          [tid-header & other-headers] headers
          non-blank? (complement str/blank?)]
      (assert (= "tid" tid-header)
              (str "Format error: Expected first line to contain column headers "
                   "with 'tid' (text ID) as the first header."))
      (utils/write-csv tsv-file (->> rows
                                     (apply map list)       ; Convert rows to columns
                                     rest                   ; Skip 'tid' column
                                     ;; Startpos and endpos are not metadata values; for the
                                     ;; other columns, construct a [header column] vector
                                     (keep-indexed (fn [index col]
                                                     (let [header (get (vec other-headers) index)]
                                                       (when-not (get #{"startpos" "endpos"} header)
                                                         [header col]))))
                                     (mapcat (fn [[header col-vals]]
                                               (map (fn [val] [(str corpus "_" header) val]) col-vals)))
                                     set
                                     (filter #(non-blank? (second %)))
                                     (cons ["corpus_cat" "value"]))))))

(defn- create-tid-config! [corpus config-path orig-tsv-path]
  (with-open [tsv-file (io/reader orig-tsv-path)]
    (let [other-cats     (->> (utils/read-csv tsv-file)
                              first
                              rest
                              (filter #(not (get #{"startpos" "endpos"} %1))))
          cat-edges      (map (fn [cat]
                                {:edge {:class                "HasMetadataValue"
                                        :lookup               (str "SELECT from MetadataValue WHERE corpus_cat = '"
                                                                   (str corpus "_" cat)
                                                                   "' AND value = ?")
                                        :joinFieldName        cat
                                        :unresolvedLinkAction "ERROR"}})
                              other-cats)
          field-removals (map (fn [cat]
                                {:field {:fieldName cat
                                         :operation "remove"}})
                              other-cats)]
      (spit config-path (-> tids-template
                            (update-in [:transformers] concat cat-edges field-removals)
                            (cheshire/generate-string {:pretty true})
                            (str/replace "###TSV-PATH###" orig-tsv-path)
                            (str/replace "###CORPUS###" corpus))))))

(defn import! [corpus]
  (let [orig-tsv-path       (-> (str "data/metadata_values/" corpus ".tsv") io/resource .getPath)
        non-tid-tsv-path    (.getPath (fs/temp-file "metadata_vals"))
        tid-config-path     (.getPath (fs/temp-file "tid_config"))
        non-tid-config-path (.getPath (fs/temp-file "metadata_val_config"))]
    (create-non-tid-tsv! corpus orig-tsv-path non-tid-tsv-path)
    (create-non-tid-config! non-tid-config-path non-tid-tsv-path)
    (create-tid-config! corpus tid-config-path orig-tsv-path)
    (utils/run-etl non-tid-config-path)
    (utils/run-etl tid-config-path)))
