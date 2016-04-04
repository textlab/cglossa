(ns cglossa.shared
  (:require [environ.core :refer [env]]
            [korma.db :as kdb]
            [clojure.java.io :as io]
            [me.raynes.conch.low-level :as sh])
  (:import (java.nio ByteBuffer)
           (java.nio.charset Charset)
           (java.io StringReader)))

(defonce corpus-connections (atom {}))

(def core-db-name (str (get env :glossa-prefix "glossa") "__core"))

(kdb/defdb core-db (kdb/mysql {:user     (:glossa-db-user env "glossa")
                               :password (:glossa-db-password env)
                               :db       core-db-name}))

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
