(ns cglossa.corpora.saami
  (:require [clojure.string :as str]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.results :refer [result-links]]
            [cglossa.result-views.cwb.speech :refer [translated-row]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod result-links "saami" [{{:keys [page-no translations]} :results-view :as a}
                                 _ result row-index]
  (let [translation-key (str @page-no "_" row-index)
        on-click        (fn [e]
                          (go
                            (let [url      "https://gtweb.uit.no/apy/translate"
                                  text     (str (str/join " "
                                                          (:full-text result))
                                                " ¥")
                                  response (<! (http/get url {:with-credentials? false
                                                              :query-params      {:langpair "sme|nob"
                                                                                  :q        text}}))
                                  trans    (get-in response [:body :responseData :translatedText])]
                              (swap! translations assoc translation-key trans))))]
    [:div {:style {:display "inline-block" :margin-left 7 :margin-right 1 :margin-bottom 2}}
     [b/button {:bs-size  "xsmall"
                :style    {:color "#666" :font-size 10}
                :on-click on-click
                :disabled (not (nil? (get @translations translation-key)))}
      "Trans"]]))

(defmethod translated-row "saami" [_ translation row-index]
  ^{:key (str "trans" row-index)}
  [:tr
   [:td "Translated by " [:a {:href "http://gtweb.uit.no/jorgal/index.nob.html?dir=sme-nob#" :target "_blank"} "Giellatekno Apertium"]]
   [:td {:col-span 3 :style {:color "#737373"}} translation]])
