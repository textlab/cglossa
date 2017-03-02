(ns cglossa.corpora.norint-opplest
  (:require [cglossa.react-adapters.bootstrap :as b]
            [cglossa.shared :refer [extra-navbar-items]]))

(defmethod extra-navbar-items "norint_opplest" [_]
  [b/navbar-text
   [b/navbar-link {:href "http://tekstlab.uio.no/norint_opplest/innlesning_hele.pdf"
                   :target "_blank"
                   :style {:color "#337ab7"}}
    "Last ned teksten"]])


