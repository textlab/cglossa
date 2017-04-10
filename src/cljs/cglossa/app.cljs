(ns cglossa.app
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [adzerk.env :as env])
  (:require [cljsjs.react]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cglossa.shared :refer [showing-metadata? extra-navbar-items]]
            [cglossa.metadata-list :refer [metadata-list]]
            [cglossa.start :refer [start]]
            [cglossa.results :refer [results]]
            [cglossa.show-texts :refer [show-texts-modal]]
            [cglossa.shared :refer [reset-queries! reset-results!]]
            [cglossa.react-adapters.bootstrap :as b]))

(env/def
  SAML_LOGIN_URL nil
  SAML_LOGOUT_URL nil)

(defn- header [{:keys [show-results?]} {:keys [corpus authenticated-user]}]
  [b/navbar {:fixed-top true}
   [b/navbar-brand "Glossa"]
   (when @show-results?
     ;; Only show corpus name in the header when showing results, since
     ;; it is shown in big letters on the front page
     [b/navbar-text (:name @corpus)])
   [extra-navbar-items corpus]
   (if (nil? @authenticated-user)
     (reset! show-login? true) ; if we want to allow anonymous users, we can show a login button here: [b/button {:on-click #(reset! show-login? true)} "Log in"]
     (do (reset! show-login? false)
         [:span.navbar-right.hidden-xs {:style {:margin-top 10}} (str "Logged in as " @authenticated-user " ")
                [b/button {:bs-size "small" :on-click #(do (set! document.cookie "session_id=; expires=Thu, 01 Jan 1970 00:00:01 GMT;")
                                                           (if (not-empty SAML_LOGOUT_URL)
                                                             (set! window.location SAML_LOGOUT_URL)
                                                             (reset! authenticated-user nil)))}
                          "Log out"]]))
   [:img.navbar-right.hidden-xs {:src "img/logo.png" :style {:margin-top 13}}]
   [:img.navbar-right.hidden-xs {:src "img/clarino_duo-219.png" :style {:width 80 :margin-top 15}}]
   ])

(defn- main-area [{:keys [show-results?] :as a} m]
  [:div.container-fluid {:style {:padding-left 50}}
   [:div.row>div#main-content.col-sm-12 {:style {:min-width 560}}
    (if @show-results?
      [results a m]
      [start a m])]])

(defn- get-models
  ([url model-state app-state]
   (go (let [response (<! (http/get url))
             body     (:body response)]
         (doseq [[model-name data] body]
           (if (http/unexceptional-status? (:status response))
             (reset! (get model-state model-name) data)
             (when (= (:status response) 401)
               (reset! (:authenticated-user model-state) nil)
               (reset! (:show-fatal-error app-state) (str body)))))))))

(defn- init [app-state model-state]
  (if-let [corpus (second (re-find #"(\w+)#?$" (.-location.href js/window)))]
    (go
      (<! (get-models (str corpus "/corpus") model-state app-state))
      (reset-queries! app-state model-state)
      (reset-results! app-state model-state))
    (reset! (:show-fatal-error app-state) "Please provide a corpus at the end of the url")))

(defn app [{:keys [show-results? show-texts?] :as a}
           {:keys [corpus authenticated-user] :as m}]
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
