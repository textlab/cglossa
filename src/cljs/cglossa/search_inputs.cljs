(ns cglossa.search-inputs
  (:require [clojure.string :as str]))

(defn- phrase->cqp [phrase phonetic?]
  (let [attr (if phonetic? "phon" "word")
        chinese-chars-range "[\u4E00-\u9FFF\u3400-\u4DFF\uF900-\uFAFF]"
        ; Surround every Chinese character by space when constructing a cqp query,
        ; to treat it as if it was an individual word:
        phrase (str/replace phrase (re-pattern (str "(" chinese-chars-range ")")) " $1 ")]
    (->> (str/split phrase #"\s")
         (map #(if (= % "")
                ""
                (str "[" attr "=\"" % "\" %c]")))
         (str/join " "))))

(defn- on-text-changed [event search-query phonetic?]
  (let [value (aget event "target" "value")
        query (if (= value "")
                ""
                (phrase->cqp value phonetic?))]
    (swap! search-query assoc-in [:query] query)))

(defn- on-phonetic-changed [event search-query]
  (let [query (:query @search-query)
        checked? (aget event "target" "checked")
        query (if checked?
                (str/replace query "word=" "phon=")
                (str/replace query "phon=" "word="))]
    (swap! search-query assoc-in [:query] query)))

(defn cwb-search-inputs [{:keys [search-query]}]
  (let [query (:query @search-query)
        displayed-query (str/replace query #"\[\(?\w+=\"(.+?)\"(?:\s+%c)?\)?\]" "$1")
        phonetic? (not= -1 (.indexOf query "phon="))]
    [:div.row-fluid
     [:form.form-inline.span12
      [:div.span10
       [:input.span12 {:type "text" :value displayed-query
                       :on-change #(on-text-changed % search-query phonetic?)
                       :on-key-down #(on-key-down % search-query)}]
       [:label {:style {:marginTop 5}}
        [:input {:name      "phonetic" :type "checkbox"
                 :style     {:marginTop -3} :checked phonetic?
                 :on-change #(on-phonetic-changed % search-query)} " Phonetic form"]]]]]))

(def components {:cwb        cwb-search-inputs
                 :cwb-speech (fn [] [:div "CWB-SPEECHE"])})
