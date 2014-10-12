(ns cljs-gui.core
  (:require
    [cljs-gui.pages.main]
    [cljs-gui.util :refer [log js-log uuid]]))

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

; (def app (js/require "app"))
; (def browser-window (js/require "browser-window"))

; ;; report atom-shell crahes
; (.start (js/require "crash-reporter"))

; (def main-window nil)

;;------------------------------------------------------------------------------
;; Global App Init
;;------------------------------------------------------------------------------

; (defn- close-main-window []
;   (set! main-window nil))

; ;; NOTE: I really have no idea why this works; just copied from their example
; (defn- destroy! []
;   (if-not (= "darwin" (aget js/process "platform"))
;     (.quit app)))

; (defn init! []
;   (set! main-window (browser-window. (js-obj "width" 800 "height" 600)))
;   (.loadUrl main-window (str "file://" + js/__dirname "/index.html"))
;   (.on main-window "closed" close-main-window))

; (.on app "ready" init!)
; (.on app "window-all-closed" destroy!)


(defn- by-id [id]
  (.getElementById js/document id))



(def child-proc (js/require "child_process"))
(def spawn (aget child-proc "spawn"))
(def exec (aget child-proc "exec"))

(def foo (spawn "lein" (array "cljsbuild" "auto") (js-obj
  "cwd" "/home/oakmac/t3tr0s/")))

(defn- on-receive-data [d]
  (aset (by-id "foo") "innerHTML"
    (str (aget (by-id "foo") "innerHTML") d))
  )

(.on (aget foo "stdout") "data" on-receive-data)



(cljs-gui.pages.main/init!)