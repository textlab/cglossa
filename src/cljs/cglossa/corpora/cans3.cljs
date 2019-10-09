(ns cglossa.corpora.cans3
  (:require [clojure.string :as str]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod result-links "cans3" [{{:keys [page-no translations]} :results-view :as a}
                                _ result row-index]
  (let [translation-key (str @page-no "_" row-index)
        on-click        (fn [e]
                          (go
                            (let [key      "PRIVATE"
                                  url      "https://www.googleapis.com/language/translate/v2"
                                  text     (str/join " " (:full-text result))
                                  response (<! (http/get url {:with-credentials? false
                                                              :query-params      {:key    key
                                                                                  :target "en"
                                                                                  :q      text}}))
                                  trans    (get-in response [:body :data :translations 0 :translatedText])]
                              (swap! translations assoc translation-key trans))))]
    [:div {:style {:display "inline-block" :margin-left 7 :margin-right 1 :margin-bottom 2}}
     [b/button {:bs-size  "xsmall"
                :style    {:color "#666" :font-size 10}
                :on-click on-click
                :disabled (not (nil? (get @translations translation-key)))}
      "Trans"]]))
