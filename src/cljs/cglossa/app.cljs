(ns cglossa.app
  (:require [cljsjs.react]
            [reagent.core :as r]
            [cglossa.shared :refer [showing-metadata?]]
            [cglossa.metadata-list :refer [metadata-list]]
            [cglossa.start :refer [start]]
            [cglossa.results :refer [results]]
            [cglossa.show-texts :refer [show-texts-modal]]
            [cglossa.react-adapters.bootstrap :as b]))

(defn- header []
  [b/navbar
   [:div.container-fluid
    [:div.navbar-header>span.navbar-brand "Glossa"]
    [:img.navbar-right.hidden-xs {:src "img/logo.png" :style {:margin-top 13}}]
    [:img.navbar-right.hidden-xs {:src "img/clarino_duo-219.png" :style {:width 80 :margin-top 15}}]]])

(defn- main-area [{{:keys [show-results?]} :results-view :as a} m]
  [:div.container-fluid {:style {:padding-left 50}}
   [:div.row>div#main-content.col-sm-12 {:style {:min-width 560}}
    (if @show-results?
      [results a m]
      [start a m])]])

(defn app [{:keys [show-texts?] :as a} {:keys [corpus] :as m}]
  (let [width (if (showing-metadata? a m) 170 0)]
    [:div
     [header]
     (when @show-texts?
       [show-texts-modal a m])
     (when @corpus
       [:div.table-display {:style {:margin-bottom 10}}
        [:div.table-row
         ^{:key "metadata-list"}
         [:div.table-cell.metadata {:style {:max-width width :width width}}
          [metadata-list a m]]
         [:div.table-cell
          [main-area a m]]]])]))
