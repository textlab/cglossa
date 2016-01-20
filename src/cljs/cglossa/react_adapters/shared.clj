(ns cglossa.react-adapters.shared
  (:require [clojure.string :as str]))

;; This needs to be a macro in order for it to access the ReactBootstrap
;; JS object in the js namespace (or is there another way?). It will define a set of Vars
;; in the namespace where the macro is called, one for each of the listed  components and
;; resulting from running reagent.core/adapt-react-class on the component. The Var names
;; will be lower-cased, for instance `button` for js/Button.
;;
;; If all the JS objects are properties of another object, provide the name of the parent
;; object with a trailing dot as the first argument string. Any dots within the remaining strings
;; will be kept as-is in the JS lookup and converted to hyphens in the names of the defined
;; Reagent components.
;;
;; For instance,
;; (adapt! "ReactBootstrap." "Button" "Dropdown" "Dropdown.Menu")
;; defines Reagent components called 'button', 'dropdown' and 'dropdown-menu' from
;; js/ReactBootstrap.Button, js/ReactBootstrap.Dropdown and js/ReactBootstrap.Dropdown.Menu.
(defmacro adapt! [& components]
  (let [prefix (if (str/ends-with? (first components) ".") (first components) nil)
        cs     (if prefix (rest components) components)]
    `(do
       ~@(for [c cs]
           `(def ~(symbol (-> c str/lower-case (str/replace \. \-)))
              (reagent.core/adapt-react-class ~(symbol "js" (str prefix c))))))))
