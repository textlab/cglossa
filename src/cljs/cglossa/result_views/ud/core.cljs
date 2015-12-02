(ns cglossa.result-views.ud.core
  (:require [cglossa.results :refer [concordance-table]]))

(defmethod concordance-table "ud" [{{:keys [results]} :results-view :as a} m]
  (when @results
    [:iframe {:width   800
              :height  800
              :src-doc (-> @results (get 1) first :text)}]))