(ns cglossa.result-views.cwb.shared
  (:require [cglossa.results :refer [result-links]]))

(defn id-column [m result]
  ;; If the 'match' property is defined, we know that we have a result from a monolingual
  ;; search or the first language of a multilingual one. If that is the case, and s-id is
  ;; defined, we print it in the first column (if we have a non-first language result, we
  ;; will include it in the next column instead).
  (when (and (:match result) (:s-id result))
    [:td (:s-id result) [result-links m result]]))

(defn text-columns [result]
  (if (:match result)
    ;; If the 'match' value is defined, we know that we have a result from a monolingual
    ;; search or the first language of a multilingual one, and then we want pre-match, match
    ;; and post-match in separate columns.
    (list ^{:key 0} [:td.left-context (:pre-match result)]
          ^{:key 1} [:td.match (:match result)]
          ^{:key 2} [:td.right-context (:post-match result)])
    ;; Otherwise, we have a result from a non-first language of a multilingual search. In that
    ;; case, CQP doesn't mark the match, so we leave the first column blank and put all of the
    ;; text in a single following column.
    (list [:td]
          [:td.aligned-text {:col-span 3} (:pre-match result)])))
