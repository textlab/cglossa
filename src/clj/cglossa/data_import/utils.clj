(ns cglossa.data-import.utils
  (:require [clojure.data.csv :as csv]))

(defn read-csv [file]
  ;; We don't use quotes around fields (since we separate them by tabs),
  ;; but clojure.data.csv will treat quotes as end-of-field unless we
  ;; set it to something else - so use a caret, which we are unlikely to
  ;; encounter in metadata values. Needs a less hackish solution of course
  ;; (maybe a patch to the csv library).
  (csv/read-csv file :separator \tab :quote \^))

(defn write-csv [file data]
  (csv/write-csv file data :separator \tab))

