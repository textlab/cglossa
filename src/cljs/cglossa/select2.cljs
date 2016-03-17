(ns cglossa.select2
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [cljsjs.jquery]
            js-select2))

(defn- unpacked
  "Dereferences the object if it implements the IDeref protocol;
  otherwise returns the object itself."
  [obj]
  (if (satisfies? IDeref obj) @obj obj))

(defn- get-select-el [component]
  (js/$ "select.list" (rdom/dom-node component)))

(defn handle-event [component event-name handler]
  (.on (get-select-el component) event-name handler))

(defn trigger-event [component event-name]
  (.select2 (get-select-el component) event-name))

(defn select2 [data value options render-body]
  "Creates a select box using the select2 jQuery plugin. options should
  be a hash map that will be converted to JS and provided as options to the
  plugin, while render-body should be a hiccup form that will be returned
  from the render function. The hiccup form should contain a select element
  with the CSS class 'list'; this is where the select2 box will be instantiated."
  (let [sort-data  #(clj->js (sort-by (fn [e] (aget e "text")) %))
        prev-data  (atom [])
        prev-value (atom nil)

        ;; Sets the entries in the list equal to the contents of the 'data'
        ;; ratom that was passed to the main function
        set-data!  (fn [sel]
                     (let [data* (unpacked data)]
                       (if (and data*
                                (not= data* @prev-data))
                         (do
                           (reset! prev-data data*)
                           (let [entries (if (sequential? data*)
                                           ;; Assume data* is already a seq of maps with
                                           ;; :id and :text keys
                                           data*
                                           ;; Assume a hashmap; convert to seq of maps with
                                           ;; :id and :text keys
                                           (map (fn [[id name]] {:id id, :text name}) data*))]
                             (.select2 sel (clj->js
                                             (merge options
                                                    {:data   entries
                                                     :sorter sort-data})))))
                         sel)))

        ;; Sets the selection of the select box to be equal to the contents of
        ;; the 'value' ratom that was passed to the main function
        set-value! (fn [sel]
                     (if (not= @value @prev-value)
                       (do
                         (reset! prev-value @value)
                         (doto sel
                           (.val (clj->js @value))
                           (.trigger "change")))
                       sel))]
    (r/create-class
      {:component-did-mount
       (fn [c]
         (-> (.select2 (get-select-el c) (clj->js options))
             (.on "change" (fn [_]
                             (this-as elem
                               (let [new-val (js->clj (.val (js/$ elem)))]
                                 (when (not= @value new-val)
                                   (reset! value new-val)
                                   (reset! prev-value new-val))))))
             (set-data!)
             (set-value!)))

       :component-will-unmount
       (fn [c] (.select2 (get-select-el c) "destroy"))

       :component-did-update
       (fn [c _] (-> (get-select-el c) (set-value!) (set-data!)))

       :render
       (fn [_]
         ;; Even though we don't actually render the contents of these
         ;; ratoms, we still need to deref them here in order for component-did-update
         ;; (where we do in fact use the ratoms) to be called. Dereferencing them only in
         ;; component-did-update is not sufficient. Note that 'data' may be either a ratom
         ;; or an ordinary seq, so we need to check if it is deref-able.
         (when (satisfies? IDeref data) (deref data))
         (deref value)
         render-body)})))
