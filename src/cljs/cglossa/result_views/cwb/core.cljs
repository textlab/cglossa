(ns cglossa.result-views.cwb.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [cglossa.shared :refer [cleanup-result]]))

(defmethod cleanup-result :default [_ result]
  (update result :text
          (fn [lines]
            (map (fn [line]
                   (-> line
                       ;; Remove the beginning of the search result, which will be a position
                       ;; number in the case of a monolingual result or the first language of a
                       ;; multilingual result, or an arrow in the case of subsequent languages
                       ;; in a multilingual result.
                       (str/replace #"^\s*\d+:\s*" "")
                       (str/replace #"^-->.+?:\s*" "")
                       ;; When the match includes the first or last token of the s unit, the XML
                       ;; tag surrounding the s unit is included inside the match braces (this
                       ;; should probably be considered a bug in CQP). We need to fix that.
                       (str/replace #"\{\{(<s_id\s+.+?>)" "$1{{")
                       (str/replace #"(</s_id>)\}\}" "}}$1")))
                 lines))))
