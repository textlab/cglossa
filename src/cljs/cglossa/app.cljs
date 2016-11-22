(ns cglossa.app
  (:require [cljsjs.react]
            [reagent.core :as r]
            [cglossa.shared :refer [showing-metadata? extra-navbar-items]]
            [cglossa.metadata-list :refer [metadata-list]]
            [cglossa.start :refer [start]]
            [cglossa.results :refer [results]]
            [cglossa.show-texts :refer [show-texts-modal]]
            [cglossa.react-adapters.bootstrap :as b]))

(defn- header [{:keys [show-results?]} {:keys [corpus]}]
  [b/navbar {:fixed-top true}
   [b/navbar-brand "Glossa"]
   (when @show-results?
     ;; Only show corpus name in the header when showing results, since
     ;; it is shown in big letters on the front page
     [b/navbar-text (:name @corpus)])
   [extra-navbar-items corpus]
   [:img.navbar-right.hidden-xs {:src "img/logo.png" :style {:margin-top 13}}]
   [:img.navbar-right.hidden-xs {:src "img/clarino_duo-219.png" :style {:width 80 :margin-top 15}}]])

(defn- main-area [{:keys [show-results?] :as a} m]
  [:div.container-fluid {:style {:padding-left 50}}
   [:div.row>div#main-content.col-sm-12 {:style {:min-width 560}}
    (if @show-results?
      [results a m]
      [start a m])]])

(defn app [{:keys [show-results? show-texts?] :as a} {:keys [corpus] :as m}]
  (let [width (if (showing-metadata? a m) 170 0)]
    [:div
     [header a m]
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
