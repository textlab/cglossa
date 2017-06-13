(ns cglossa.shared
  (:require [environ.core :refer [env]]
            [korma.db :as kdb]
            [clojure.java.io :as io]
            [me.raynes.conch.low-level :as sh])
  (:import (java.nio ByteBuffer)
           (java.nio.charset Charset)
           (java.io StringReader)))

(defonce corpus-connections (atom {}))

;; OVERRIDE OUTDATED mysql FUNCTION IN KORMA
(defn mysql
  "Create a database specification for a mysql database. Opts should include keys
  for :db, :user, and :password. You can also optionally set host and port.
  Delimiters are automatically set to \"`\"."
  [{:keys [host port db make-pool?]
    :or   {host "127.0.0.1", port 3306, db "", make-pool? true}
    :as   opts}]
  ;; TODO: Use the updated driver when we can update the MySQL connector (requires Java 1.8 or recent MySQL?)
  (merge {#_:classname   #_"com.mysql.cj.jdbc.Driver" ; must be in classpath - UPDATED DRIVER
          :classname "com.mysql.jdbc.Driver" ; must be in classpath
          :subprotocol "mysql"
          :subname     (str "//" host ":" port "/" db "?useSSL=false&serverTimezone=CET") ; ADD useSSL and serverTimezone
          :delimiters  "`"
          :make-pool?  make-pool?}
         (dissoc opts :host :port :db)))

(def core-db-name (get env :glossa-core (str (get env :glossa-prefix "glossa") "__core")))

(kdb/defdb core-db (mysql {:user     (:glossa-db-user env "glossa")
                           :password (:glossa-db-password env)
                           :useUnicode true
                           :characterEncoding "UTF-8"
                           :db       core-db-name
                           :host     "127.0.0.1"
                           :port     3306}))

(defn convert-string [s from-charset-name to-charset-name]
  (let [from-charset (Charset/forName from-charset-name)
        to-charset   (Charset/forName to-charset-name)]
    ;; Do we seriously have to do all this to convert a Java string between two charsets??
    (->> s .getBytes ByteBuffer/wrap (.decode from-charset) (.encode to-charset) .array String.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; A slightly modified implementation of the feed-from-string function
;; from me.raynes.conch.low-level which fixes a problem with sending options.
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn feed-from
  "Feed to a process's input stream with optional. Options passed are
  fed to clojure.java.io/copy. They are :encoding to set the encoding
  and :buffer-size to set the size of the buffer. :encoding defaults to
  UTF-8 and :buffer-size to 1024. If :flush is specified and is false,
  the process will be flushed after writing."
  [process from & {flush? :flush :or {flush? true} :as all}]
  ;; Here we need to convert 'all' to a sequence - in the original it is a hash map,
  ;; which won't work
  (apply io/copy from (:in process) (flatten (seq all)))
  (when flush? (sh/flush process)))

(defn feed-from-string
  "Feed the process some data from a string."
  [process s & args]
  (apply feed-from process (StringReader. s) args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
