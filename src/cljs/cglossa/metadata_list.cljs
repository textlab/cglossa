(ns cglossa.metadata-list
  (:require [reagent.core :as r]
            [com.rpl.specter :as s]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.select2 :as sel])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def auto-opening-select (with-meta sel/select2
                                    {:component-did-mount  #(sel/trigger-event % "open")
                                     :component-did-update #(sel/trigger-event % "open")}))

(defn- val-get-set [metadata-categories]
  (fn
    ([cat-id]
     (s/select-first [s/ALL #(= (:rid %) cat-id) :values] @metadata-categories))
    ([cat-id v]
     (let [new-val (s/setval [s/ALL #(= (:rid %) cat-id) :values] v @metadata-categories)]
       (reset! metadata-categories new-val)))))

(defn metadata-list [{:keys [open-metadata-cat]} {:keys [search metadata-categories]}]
  [:span
   (doall
     (for [cat @metadata-categories
           :let [cat-id (:rid cat)
                 open?  (or
                          ;; The user has opened this select list
                          (= @open-metadata-cat cat-id)
                          ;; The search contains a non-empty seq of values from this category
                          (seq (get-in @search [:metadata cat-id])))]]
       ^{:key cat-id}
       [:div.metadata-category
        [:a {:href "#" :on-click (fn [e]
                                   (reset! open-metadata-cat (if open? nil cat-id))
                                   (.preventDefault e))}
         (:name cat)]
        (when open?
          (let [values (r/cursor (val-get-set metadata-categories) cat-id)]
            (when (empty? @values)
              (go
                (let [{req-values :body} (<! (http/get "/metadata-values"
                                                       {:query-params {:cat-id cat-id}}))]
                  (reset! values req-values))))
            (list
              ^{:key (str "close-btn" cat-id)}
              [b/button {:bs-size    "xsmall"
                         :bs-style   "danger"
                         :title      "Remove selection"
                         :class-name "close-cat-btn"
                         :on-click   #(reset! open-metadata-cat nil)}
               [b/glyphicon {:glyph "remove"}]]
              ^{:key (str "select" cat-id)}
              [auto-opening-select values (r/atom nil) {:placeholder "Click to select..."}
               [:div
                [:select.list {:style {:width "90%"} :multiple true}]]])))]))])
