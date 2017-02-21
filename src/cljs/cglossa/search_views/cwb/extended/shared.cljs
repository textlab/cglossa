(ns cglossa.search-views.cwb.extended.shared
  (:require [clojure.string :as str]
            [cglossa.react-adapters.bootstrap :as b]))

(defn language-data [corpus lang-code]
  (->> (:languages @corpus) (filter #(= (:code %) lang-code)) first))

(defn language-menu-data [corpus lang-code]
  (:menu-data (language-data corpus lang-code)))

(defn language-config [corpus lang-code]
  (:config (language-data corpus lang-code)))

(defn language-corpus-specific-attrs [corpus lang-code]
  (:corpus-specific-attrs (language-data corpus lang-code)))

(defn tag-description-label [value description tooltip path wrapped-term show-attr-popup?]
  (let [excluded? (or (str/starts-with? description "!") (str/includes? description ":!"))]
    [b/label {:bs-style    (if excluded? "danger" "primary")
              :data-toggle (when tooltip "tooltip")
              :title       tooltip
              :style       {:float        "left"
                            :margin-top   3
                            :margin-right 3
                            :cursor       "pointer"}
              :on-click    #(reset! show-attr-popup? true)}
     description [:span {:style    {:margin-left 6 :cursor "pointer"}
                         :on-click (fn [e]
                                     (.stopPropagation e)
                                     ;; Need to manually remove the tooltip of our parent
                                     ;; label; otherwise the tooltip may persist after
                                     ;; the label has been removed (and then there is no way
                                     ;; to remove it).
                                     (-> (.-target e)
                                         js/$
                                         (.closest "[data-toggle]")
                                         (.tooltip "destroy"))
                                     (swap! wrapped-term update-in path (fn [o]
                                                                          (if (map? o)
                                                                            (dissoc o value)
                                                                            (disj o value))))
                                     ;; Remove the entire category (e.g. a specific POS) if it
                                     ;; became empty after the update we just did
                                     (when (empty? (get-in @wrapped-term path))
                                       (let [path*     (butlast path)
                                             attr-name (last path)]
                                         (swap! wrapped-term update-in path* dissoc attr-name))))}
                  "x"]]))


