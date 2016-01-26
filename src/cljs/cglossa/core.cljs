(ns cglossa.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [devtools.core :as devtools]
            [cglossa.search-engines]                        ; just to pull in implementations
            [cglossa.shared :refer [reset-queries!]]
            [cglossa.app :refer [app]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import [goog Throttle]))

(devtools/enable-feature! :dirac)
(devtools/set-pref! :install-sanity-hints true)
(devtools/install!)

(defn narrow-view? []
  (< (.-innerWidth js/window) 768))

(defonce app-state {:narrow-view?      (r/atom (narrow-view?))
                    :show-metadata?    (r/atom nil)
                    :show-texts?       (r/atom false)
                    :results-view      {:show-results?      (r/atom false)
                                        :results            (r/atom nil)
                                        :total              (r/atom nil)
                                        :page-no            (r/atom 1)
                                        ;; This is the page selected in the result paginator;
                                        ;; it may differ from the one shown in the result table
                                        ;; until the selected page has been fetched from the server
                                        :paginator-page-no  (r/atom 1)
                                        ;; This is the value shown in the paginator text input.
                                        ;; It may differ from paginator-page-no while we are
                                        ;; manually editing the value, but will be set equal
                                        ;; to paginator-page-no when we hit Enter after editing
                                        ;; or we select a different page using the paging buttons.
                                        :paginator-text-val (r/atom 1)
                                        ;; Set of result pages currently being fetched
                                        :fetching-pages     (r/atom #{})
                                        :sort-key           (r/atom :position)
                                        :freq-attr          (r/atom nil)
                                        :media              {:player-row-index    (r/atom nil)
                                                             :current-player-type (r/atom nil)
                                                             :current-media-type  (r/atom nil)}}
                    :search-view       {:view-type           (r/atom :simple)
                                        :queries             (r/atom [{:query ""}])
                                        :query-ids           (r/atom nil)
                                        :show-attr-popup-for (r/atom nil)}
                    :searching?        (r/atom false)
                    :open-metadata-cat (r/atom nil)
                    :num-resets        (r/atom 0)})

(defonce model-state {:corpus              (r/atom nil)
                      :metadata-categories (r/atom nil)
                      :gram-titles         (r/atom nil)
                      :menu-data           (r/atom nil)
                      :search              (r/atom {})})

;; Set :narrow-view in app-state whenever the window is resized (throttled to 200ms)
(def on-resize-throttle (Throttle. #(reset! (:narrow-view? app-state) (narrow-view?)) 200))
(.addEventListener js/window "resize" #(.fire on-resize-throttle))

(defn- get-models
  ([url] (get-models url {}))
  ([url params]
   (go (let [response (<! (http/get url {:query-params params}))
             body     (:body response)]
         (doseq [[model-name data] body]
           (if (http/unexceptional-status? (:status response))
             (reset! (get model-state model-name) data)
             (.error js/console (str "Error: " body))))))))

(if-let [corpus (second (re-find #"corpus=(\w+)" (.-location.search js/window)))]
  (go
    (<! (get-models "/corpus" {:code corpus}))
    (reset-queries! app-state model-state)
    (let [corpus         @(:corpus model-state)
          languages      (:languages corpus)
          language-codes (for [{{:keys [code]} :lang} languages] code)
          gram-titles    (zipmap language-codes @(:gram-titles model-state))
          menu-data      (zipmap language-codes @(:menu-data model-state))]
      (reset! (:gram-titles model-state) gram-titles)
      (reset! (:menu-data model-state) menu-data)))
  (js/alert "Please provide a corpus in the query string (on the form corpus=mycorpus)"))

(defn ^:export main []
  (rdom/render
    [app app-state model-state]
    (. js/document (getElementById "app"))))

(main)
