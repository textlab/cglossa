(ns cglossa.search-views.ud.core
  (:require [reagent.core :as r]
            [cglossa.react-adapters.bootstrap :as b]
            [cglossa.shared :refer [search! on-key-down]]
            [cglossa.search-views.shared :refer [search-inputs]]))

(def languages ["English"
                "Ancient_Greek"
                "Ancient_Greek-PROIEL"
                "Arabic"
                "Basque"
                "Bulgarian"
                "Croatian"
                "Czech"
                "Danish"
                "Dutch"
                "Estonian"
                "Finnish"
                "Finnish-FTB"
                "French"
                "German"
                "Gothic"
                "Greek"
                "Hebrew"
                "Hindi"
                "Hungarian"
                "Indonesian"
                "Irish"
                "Italian"
                "Japanese-KTC"
                "Latin"
                "Latin-ITT"
                "Latin-PROIEL"
                "Norwegian"
                "Old_Church_Slavonic"
                "Persian"
                "Polish"
                "Portuguese"
                "Romanian"
                "Slovenian"
                "Spanish"
                "Swedish"
                "Tamil"
                "Fi-Parsebank-50M"
                "Fi-Parsebank-1M"])

(defmethod search-inputs "ud" [{{:keys [queries]} :search-view :as a} m]
  [:span
   [:div.row.search-input-links
    [:div.col-sm-3
     [b/input {:type          "select"
               :style         {:width 150}
               :default-value (:lang (first @queries))
               :on-change     #(swap! queries assoc-in [0 :lang] (.-target.value %))}
      (for [language languages]
        [:option {:key language :value language} language])]]
    [:div.col-sm-9
     [b/button {:bs-style "success"
                :style    {:margin-left 233}
                :on-click #(search! a m)} "Search"]]]
   [:div.row
    [:div.col-sm-12
     [:form.table-display {:style {:margin "10px 0px 15px 0px"}}
      [:div.table-row {:style {:margin-bottom 10}}
       [b/input {:style            {:width 522}
                 :class-name       "col-sm-12"
                 :group-class-name "table-cell"
                 :type             "textarea"
                 :default-value    (:query (first @queries))
                 :on-change        #(swap! queries assoc-in [0 :query] (.-target.value %))
                 :on-key-down      #(on-key-down % a m)}]]]]]])