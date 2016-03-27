(ns cglossa.search-views.cwb.extended.shared)

(defn language-data [corpus lang-code]
  (->> (:languages @corpus) (filter #(= (:code %) lang-code)) first))

(defn language-menu-data [corpus lang-code]
  (:menu-data (language-data corpus lang-code)))

(defn language-config [corpus lang-code]
  (:config (language-data corpus lang-code)))

(defn language-corpus-specific-attrs [corpus lang-code]
  (:corpus-specific-attrs (language-data corpus lang-code)))
