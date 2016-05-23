(ns cglossa.react-adapters.bootstrap
  (:require react-bootstrap
            reagent.core)
  (:require-macros [cglossa.react-adapters.shared :refer [adapt!]]))

;; This will define a set of Vars in the current namespace, one for each of the
;; listed Bootstrap components and resulting from running reagent.core/adapt-react-class
;; on the component. The Var names will be lower-cased, and hyphenated in the case of
;; sub-objects, for instance `button` for js/ReactBootstrap.Button and `dropdown-menu` for
;; js/ReactBootstrap.Dropdown.Menu, so they should be referred like this in other namespaces:
;; (:require [cglossa.react-adapters.bootstrap :refer [button modal label dropdown-menu])
(adapt! "ReactBootstrap."
        "Button"
        "ButtonToolbar"
        "Dropdown"
        "Dropdown.Menu"
        "Dropdown.Toggle"
        "DropdownButton"
        "Glyphicon"
        "Input"
        "Label"
        "MenuItem"
        "Modal"
        "ModalBody"
        "ModalFooter"
        "ModalHeader"
        "ModalTitle"
        "Navbar"
        "Overlay"
        "OverlayTrigger"
        "Panel"
        "Tabs"
        "Tab"
        "Table"
        "Tooltip")
