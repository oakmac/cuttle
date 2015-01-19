(ns cuttle.dom
  (:require
    [cuttle.util :refer [js-log log]]))

;;------------------------------------------------------------------------------
;; DOM Helper Functions
;;------------------------------------------------------------------------------

(defn by-id [id]
  (.getElementById js/document id))

(defn set-html! [id html]
  (aset (by-id id) "innerHTML" html))

(defn show-el! [id]
  (aset (by-id id) "style" "display" ""))

(defn hide-el! [id]
  (aset (by-id id) "style" "display" "none"))

(defn remove-el! [id]
  (let [el (by-id id)
        parent-el (aget el "parentNode")]
    (.removeChild parent-el el)))
