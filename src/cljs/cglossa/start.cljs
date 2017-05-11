(ns cglossa.start
  (:require [cglossa.search-views.shared :refer [search-inputs]]
            [cglossa.shared :refer [top-toolbar]]
            [cglossa.react-adapters.bootstrap :as b]))

(defn- corpus-info [_ {:keys [corpus]}]
  ;; Remove the background image (gradient, really) set by bootstrap-theme,
  ;; since the unthemed well is actually nicer.
  [:div.row.corpus-info>div.col-sm-12
   [:div.well {:style {:background-image "url()"}}
    [:h2
     (:name @corpus)
     (when-let [logo (:logo @corpus)]
       (let [logo-path (if (re-find #"^https?:" logo) logo (str "img/" logo))]
         [:img.corpus-logo {:src (str logo-path)}]))]]])

(defn start [{:keys [num-resets] :as a} {:keys [corpus] :as m}]
  [:div
   [:div.row
    [top-toolbar a m]]
   [corpus-info a m]
   ;; Using num-resets as key is a hackish way to force reagent to
   ;; re-mount the currently selected search inputs each time the form
   ;; is reset and num-resets incremented. Since the text inputs are uncontrolled
   ;; (to prevent the cursor from jumping to the end when we edit them)
   ;; we need to re-mount them in order for them to set the new, blank
   ;; query as their value.
   ^{:key @num-resets} [search-inputs a m]
   (when (:corpus-info @corpus)
     [:div {:style {:margin-top 30}
            :dangerouslySetInnerHTML {"__html" (:corpus-info @corpus)}}])])
