(ns cljs-gui.lein
  (:require
    [cljs-gui.util :refer [log js-log uuid]]))

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def child-proc (js/require "child_process"))
(def spawn (aget child-proc "spawn"))
(def exec (aget child-proc "exec"))

;;------------------------------------------------------------------------------
;; Helper
;;------------------------------------------------------------------------------

(defn- project-file->cwd [f]
  (.replace f #"/[a-zA-Z0-9]+\.clj$" "/"))

(defn- project-file? [f]
  (= -1 (.indexOf f #"\.clj$")))

;; TODO: this function needs a better name
;; also we should probably do some checking on valid cwd format
(defn- convert-cwd [cwd]
  (if (project-file? cwd)
    (project-file->cwd cwd)
    cwd))

;;------------------------------------------------------------------------------
;; Public Methods
;;------------------------------------------------------------------------------

; (def foo (spawn "lein"
;   (array "cljsbuild" "auto")
;   (js-obj "cwd" "/home/oakmac/t3tr0s/")))

; (defn- on-receive-data [d]
;   (aset (by-id "foo") "innerHTML"
;     (str (aget (by-id "foo") "innerHTML") d)))

; (.on (aget foo "stdout") "data" on-receive-data)

; (.on foo "close" (fn [c]
;   (log "child process was closed. hooray!")))


(defn auto []
  ;; TODO: write this
  ;; punting on this for now because I'm having trouble killing a long-running
  ;; spawned process
  )

(defn- build-once-cmd [blds]

  )

(defn build-once [cwd blds output-fn]

  )

;; TODO: capture better error results from this
(defn clean [cwd success-fn error-fn]
  (exec "lein cljsbuild clean"
    (js-obj "cwd" (convert-cwd cwd))
    (fn [err _stdout _stderr]
      (if err
        (error-fn)
        (success-fn)))))