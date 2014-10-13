(ns cljs-gui.lein
  (:require
    [clojure.string :refer [replace trim]]
    [cljs-gui.util :refer [log js-log uuid]]))

(declare extract-target-from-start-msg)

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def child-proc (js/require "child_process"))
(def spawn (aget child-proc "spawn"))
(def exec (aget child-proc "exec"))

;;------------------------------------------------------------------------------
;; Paser Compiler Output
;;------------------------------------------------------------------------------

;; NOTE: this probably belongs in it's own library or emitted as EDN from the
;; compiler itself
;; we can get by with regex and duct tape for now ;)

(defn- error-line? [s]
  (and (not= -1 (.search s #"Caused by: "))
       (not= -1 (.search s #"at c"))))

(defn- start-line? [s]
  (and (not= -1 (.search s #"^Compiling "))
       (not= -1 (.search s #"]\.\.\.$"))))

(defn- success-line? [s]
  (and (not= -1 (.search s #"Successfully compiled"))
       (not= -1 (.search s #"seconds\."))))

(defn- warning-line? [s]
  (not= -1 (.search s #"^WARNING: ")))

(defn- output-type?
  "Returns the type of line output from the compiler.
   One of :error :start :success :warning
   nil if we do not recognize the line or don't care what it is"
  [s]
  (cond
    (error-line? s) :error
    (start-line? s) :start
    (success-line? s) :success
    (warning-line? s) :warning
    :else nil))

(defn- extract-time-from-success-msg [s]
  (-> s
    (replace #"^.+ in " "")
    (replace #" seconds.+$" "")
    float))

(defn- extract-error-msg [s]
  (-> s
    (replace "\n" " ")
    (replace "\t" "")
    (replace #"^.*Caused by: " "")
    (replace #"} at .*$" "}")))

(defn- extract-warning-msgs [s]
  ;; TODO: write me
  nil
  )

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

(defn auto []
  ;; TODO: write this
  ;; punting on this for now because I'm having trouble killing a long-running
  ;; spawned process
  )

(defn- build-once-close []
  (log "build-once process closed!"))

(defn- show-start-build [project-key bld-key bld-target]

  )

(def current-build (atom nil))

(defn- build-once-stdout [output-chunk]
  (let [chunk2 (trim output-chunk)
        type (output-type? chunk2)]
    (cond
      (= type :error)
        (let [error-msg (extract-error-msg chunk2)]
          (log :error)
          (js-log chunk2)
          (js-log "~~~~break~~~~")
          (js-log error-msg)
          (js-log "-----------------------------"))
      (= type :start)
        (let [target (extract-target-from-start-msg chunk2)]
          (log :start)
          (js-log chunk2)
          (log target)
          (js-log "-----------------------------"))
      (= type :success)
        (let [compile-time (extract-time-from-success-msg chunk2)]
          (log :success)
          (js-log chunk2)
          (log compile-time)
          (js-log "-----------------------------"))
      (= type :warning)
        (let [warning-msgs (extract-warning-msgs chunk2)]
          (log :warning)
          (js-log chunk2)
          (log warning-msgs)
          (js-log "-----------------------------"))
      :else nil)))

    ; (when (= type :start)
    ;   (js-log "-----------------------------------")
    ;   (log type)
    ;   (js-log chunk2)
    ;   (js-log (extract-target-from-start-msg chunk2)))
    ; (when (= type :success)
    ;   (js-log "-----------------------------------")
    ;   (log type)
    ;   (js-log chunk2)
    ;   (js-log (extract-time-from-success-msg chunk2)))
    ; (when type
    ;   (js-log "----------------------------------------------")
    ;   (log type)
    ;   (js-log chunk2))))

(defn build-once [cwd blds output-fn]
  (let [child (spawn "lein"
                (array "cljsbuild" "once")
                (js-obj "cwd" (convert-cwd cwd)))]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data" build-once-stdout)
    (.on (.-stdout child) "data" build-once-stdout)
    (.on child "close" build-once-close)))

;; TODO: capture better error results from this
(defn clean [cwd success-fn error-fn]
  (exec "lein cljsbuild clean"
    (js-obj "cwd" (convert-cwd cwd))
    (fn [err _stdout _stderr]
      (if err
        (error-fn)
        (success-fn)))))













;; NOTE: sublime text syntax highlighting chokes on this function, so I put it
;; at the bottom of the file

(defn- extract-target-from-start-msg [s]
  (-> s
    (replace "Compiling \"" "")
    (replace #"\".+$" "")))
