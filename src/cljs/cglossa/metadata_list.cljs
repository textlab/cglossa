(ns cglossa.metadata-list
  (:require [reagent.core :as r :include-macros true]
            [cljs.core.async :refer [<!]]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.select2 :as sel]
            [cglossa.shared :refer [search!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- metadata-select [a m corpus cat-id search selected open-metadata-cat]
  (r/create-class
    {:component-did-mount
     ;; If the user has explicitly selected this category by clicking on its header/link,
     ;; we open the select list automatically (if, on the other hand, we are showing the
     ;; category just because the search contains some values from it, we will only show
     ;; its text input and not its list). We also need to make sure that the list is no
     ;; longer opened automatically after the user closes it.
     (when (= @open-metadata-cat cat-id)
       (fn [c]
         (sel/handle-event c "select2:select" #(search! a m))
         (sel/handle-event c "select2:unselect" #(search! a m))
         (sel/handle-event c "select2:close" #(reset! open-metadata-cat nil))
         (sel/trigger-event c "open")))

     :render
     (fn [_]
       ;; We set the first argument to select2 to be nil, meaning that its data will be fetched
       ;; via the ajax option (i.e. fetched remotely on demand) instead of being stored in a
       ;; state ratom.
       [sel/select2 nil selected
        {:placeholder "Click to select..."
         :ajax        {:url  "/metadata-values"
                       :data (fn [params]
                               (let [md           (as-> @search $
                                                        (:metadata $)
                                                        (dissoc $ cat-id)
                                                        (filter #(second %) $)
                                                        (into {} $))
                                     value-filter (if (empty? (.-term params))
                                                    js/undefined
                                                    (.-term params))
                                     selected-ids (if (empty? md)
                                                    js/undefined
                                                    (js/JSON.stringify (clj->js md)))]
                                 #js {:corpus-id    (:id @corpus)
                                      :category-id  cat-id
                                      :value-filter value-filter
                                      :selected-ids selected-ids
                                      :page         (.-page params)}))}}
        [:div
         [:select.list {:style {:width "90%"} :multiple true}]]])}))

(defn metadata-list [{:keys [open-metadata-cat] :as a}
                     {:keys [corpus search metadata-categories] :as m}]
  (r/with-let [remove-cat-values (fn [cat-id]
                                   (reset! open-metadata-cat nil)
                                   (swap! search update :metadata dissoc cat-id)
                                   (when (empty? (:metadata @search))
                                     (swap! search dissoc :metadata))
                                   (search! a m))]
    [:span
     (doall
       (for [cat @metadata-categories
             :let [cat-id   (:id cat)
                   selected (r/cursor search [:metadata cat-id])
                   ;; Show the select2 component for this category if the user has
                   ;; explicitly opened it or the search contains a non-empty seq of
                   ;; values from this category
                   show?    (or (= @open-metadata-cat cat-id)
                                (seq @selected))]]
         ^{:key cat-id}
         [:div.metadata-category
          [:a {:href "#" :on-click (fn [e]
                                     (if show?
                                       (remove-cat-values cat-id)
                                       (reset! open-metadata-cat cat-id))
                                     (.preventDefault e))}
           (or (:name cat) (-> cat :code (str/replace "_" " ") str/capitalize))]
          (when show?
            (list
              ^{:key (str "close-btn" cat-id)}
              [b/button {:bs-size    "xsmall"
                         :bs-style   "danger"
                         :title      "Remove selection"
                         :class-name "close-cat-btn"
                         :on-click   #(remove-cat-values cat-id)}
               [b/glyphicon {:glyph "remove"}]]
              ^{:key (str "select" cat-id)}
              [metadata-select a m corpus cat-id search selected open-metadata-cat]))]))]))
