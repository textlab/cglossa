(ns cglossa.show-texts
  (:require [cglossa.react-adapters.bootstrap :as b]))

(defn show-texts-modal [{:keys [show-texts?]}]
  (let [hide (fn [e]
               (reset! show-texts? false)
               (.preventDefault e))]
    (fn [_]
      [b/modal {:show    true
                :on-hide hide}
       [b/modalheader {:close-button true}
        [b/modaltitle "Corpus texts"]]
       [b/modalbody "AAA"]
       [b/modalfooter
        [b/button {:on-click hide} "Close"]]])))