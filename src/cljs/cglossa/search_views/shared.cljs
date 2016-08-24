(ns cglossa.search-views.shared)

(defmulti search-inputs
  "Multimethod that accepts two arguments - an app state map and a
   model/domain state map - and dispatches to the correct method based on the
   value of :search-engine in the corpus map found in the model/domain state
   map. The :default case implements CWB support."
  (fn [_ {corpus :corpus}] (:search-engine @corpus)))

(defn has-phonetic? [corpus]
  "Determines whether a corpus contains phonetic transcriptions by checking if
   its first (and probably only) language includes 'phon' among its displayed
   attributes."
  (->> corpus :languages first :config :displayed-attrs (map first) (some #{:phon})))
