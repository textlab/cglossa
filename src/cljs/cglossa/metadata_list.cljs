(ns cglossa.metadata-list
  (:require [clojure.string :as str]
            [reagent.core :as r :include-macros true]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.select2 :as sel]
            [cglossa.shared :refer [selected-metadata-ids queries->param search!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- count-selected-texts! [{:keys [num-selected-texts]} {:keys [corpus search] :as m}]
  (go
    (let [results-ch (http/post (str (:code @corpus) "/num-texts")
                                {:json-params
                                 {:selected-metadata-ids (selected-metadata-ids search)}})
          {:keys [status success body]} (<! results-ch)]
      (when (= status 401)
        (reset! (:authenticated-user m) nil))
      (if-not success
        (.log js/console status)
        ;; If we get a nil from the server, it means that all texts were selected
        ;; (i.e., no metadata was selected). Setting the atom to nil makes the list
        ;; display "All texts selected".
        (reset! num-selected-texts (when body
                                     (js/parseInt body)))))))

(defn- count-selected-tokens! [{:keys             [num-selected-tokens]
                                {:keys [queries]} :search-view}
                               {:keys [corpus search] :as m}]
  (go
    (let [results-ch (http/post (str (:code @corpus) "/num-tokens")
                                {:json-params
                                 {:queries               (queries->param @corpus @queries)
                                  :selected-metadata-ids (selected-metadata-ids search)}})
          {:keys [status success body]} (<! results-ch)]
      (when (= status 401)
        (reset! (:authenticated-user m) nil))
      (if-not success
        (.log js/console status)
        (reset! num-selected-tokens (when body
                                      (js/parseInt body)))))))

(defn get-text-selection-info! [{:keys [text-selection-info]} {:keys [corpus search] :as m}]
  (go
    (let [results-ch (http/post (str (:code @corpus) "/text-selection-info")
                                {:json-params
                                 {:selected-metadata-ids (selected-metadata-ids search)}})
          {:keys [status success body]} (<! results-ch)]
      (when (= status 401)
        (reset! (:authenticated-user m) nil))
      (if-not success
        (.log js/console status)
        (reset! text-selection-info body)))))

(defn- metadata-selection-changed [{:keys [orig-queries] :as a} {:keys [search] :as m}]
  (count-selected-texts! a m)
  (count-selected-tokens! a m)
  (get-text-selection-info! a m)
  ;; We need a new search id every time metadata selection is changed,
  ;; otherwise sorting won't work correctly:
  (swap! search dissoc :id)
  (reset! orig-queries nil)
  (search! a m))

(defn text-selection [{:keys [num-selected-texts num-selected-tokens text-selection-info]}
                      {:keys [corpus]}]
  (let [tid-type    (if (= (:search-engine @corpus) "cwb_speech") "informants" "texts")
        sel-texts   (if (or (nil? @num-selected-texts)
                            (= @num-selected-texts (:num-texts @corpus)))
                      "All "
                      (str @num-selected-texts " of "))
        corpus-size (second (first (get-in @corpus [:extra-info :size])))
        sel-tokens  (if (or (nil? @num-selected-tokens)
                            (= @num-selected-tokens corpus-size))
                      corpus-size
                      (str @num-selected-tokens " of " corpus-size))]
    [:div {:style {:color "#676767"}}
     (str sel-texts (:num-texts @corpus) " " tid-type " (" sel-tokens " tokens) selected"
          @text-selection-info)]))

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
         (sel/handle-event c "select2:select" #(metadata-selection-changed a m))
         (sel/handle-event c "select2:unselect" #(metadata-selection-changed a m))
         (sel/handle-event c "select2:close" #(reset! open-metadata-cat nil))
         (sel/trigger-event c "open")))

     :render
     (fn [_]
       ;; We set the first argument to select2 to be nil, meaning that its data will be fetched
       ;; via the ajax option (i.e. fetched remotely on demand) instead of being stored in a
       ;; state ratom.
       [sel/select2 nil selected
        {:placeholder "Click to select..."
         :ajax        {:url  (str (:code @corpus) "/metadata-values")
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
                                 #js {:category-id  cat-id
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
                                   (metadata-selection-changed a m))]
    [:span
     [:div {:style {:margin-left 15}}
      [text-selection a m]]
     (doall
       (for [cat @metadata-categories
             :when (not (str/ends-with? (:code cat) "_hd"))
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
