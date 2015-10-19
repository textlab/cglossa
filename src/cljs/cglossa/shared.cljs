(ns cglossa.shared
  (:require [cglossa.react-adapters.bootstrap :as b]))

(defn showing-metadata? [{:keys [show-metadata? narrow-view?]
                          {:keys [show-results?]} :results-view}
                         {:keys [metadata-categories]}]
  (cond
    ;; Don't show metadata if the corpus doesn't have any (duh!)
    (empty? @metadata-categories) false
    ;; If show-metadata is non-nil, the user has explicitly chosen whether to see metadata,
    ;; so we respect that unconditionally
    (some? @show-metadata?) @show-metadata?
    ;; Now we know that we have metadata, and that the user has not explicitly chosen
    ;; whether to see them. If we are showing search results, we hide the metadata if the
    ;; window is narrow; if instead we are showing the start page, we show the metadata
    ;; regardless of window size.
    @show-results? (not @narrow-view?)
    :else true))

(defn top-toolbar [{:keys [num-resets show-metadata?] {:keys [queries]} :search-view :as a} m]
  [:div.col-sm-5
   [b/buttontoolbar {:style {:margin-bottom 20}}
    (if (showing-metadata? a m)
      [b/button {:bs-size  "xsmall"
                 :title    "Hide search criteria"
                 :on-click (fn [e]
                             (reset! show-metadata? false)
                             (.preventDefault e))}
       "Hide filters"]
      [b/button {:bs-size  "xsmall"
                 :title    "Show search criteria"
                 :on-click (fn [e]
                             (reset! show-metadata? true)
                             (.preventDefault e))}
       "Filters"])
    [b/button {:bs-style "primary"
               :bs-size  "xsmall"
               :title    "Reset form"
               :on-click (fn []
                           (reset! queries [{:query ""}])
                           (swap! num-resets inc))}         ; see comments in the start component
     "Reset form"]]])
