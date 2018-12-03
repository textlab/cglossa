(ns cglossa.search-views.shared)

(defmulti search-inputs
  "Multimethod that accepts two arguments - an app state map and a
   model/domain state map - and dispatches to the correct method based on the
   value of :search-engine in the corpus map found in the model/domain state
   map. The :default case implements CWB support."
  (fn [_ {corpus :corpus}] (:search-engine @corpus)))

(defn- check-attr [corpus attr]
  (->> corpus :languages first :config :displayed-attrs (map first) (some #{attr})))

(defn- get-attr [corpus attr]
  (->> corpus :languages first :config :displayed-attrs (flatten) (apply hash-map) attr))

(defn has-lemma? [corpus]
  "Determines whether a corpus contains lemmas by checking if its first language includes 'lemma'
  among its displayed attributes. This assumes that, in a multilingual corpus, either all languages
  are tagged (possibly using dummy lemmas and tags) or they are all untagged."
  (check-attr corpus :lemma))

(defn has-phonetic? [corpus]
  "Determines whether a corpus contains phonetic transcriptions by checking if
   its first (and probably only) language includes 'phon' among its displayed
   attributes."
  (check-attr corpus :phon))

(defn get-phonetic [corpus]
  "Get the display name of the phonetic attribute."
  (get-attr corpus :phon))

(defn has-original? [corpus]
  "Determines whether a corpus contains original in addition to corrected text by checking if
   its first (and probably only) language includes 'orig' among its displayed
   attributes."
  (check-attr corpus :orig))
