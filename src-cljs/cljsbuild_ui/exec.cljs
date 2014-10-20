(ns cljsbuild-ui.exec
  (:require
    [clojure.string :refer [replace split-lines split trim]]
    [cljs.core.async :refer [chan close! put!]]
    [cljsbuild-ui.util :refer [log js-log on-windows? uuid]]))

(declare extract-target-from-start-msg)

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def child-proc (js/require "child_process"))
(def js-exec (aget child-proc "exec"))
(def js-spawn (aget child-proc "spawn"))

;;------------------------------------------------------------------------------
;; Paser Compiler Output
;;------------------------------------------------------------------------------

;; NOTE: this probably belongs in it's own library or emitted as EDN from the
;; compiler itself
;; we can get by with regex and duct tape for now ;)

(defn- start-error-line? [s]
  (and (.test #"Compiling " s)
       (.test #"failed\." s)))

(defn- end-error-line? [s]
  (.test #"Subprocess failed" s))

(def end-error-msg (str "***END ERROR***" (uuid)))

(defn- end-error-line? [s]
  (or (.test #"Subprocess failed" s)
      (= s end-error-msg)))

(defn- start-line? [s]
  (and (.test #"Compiling " s)
       (.test #"]\.\.\.$" s)))

(defn- success-line? [s]
  (and (.test #"Successfully compiled" s)
       (.test #"seconds\." s)))

(defn- warning-line? [s]
  (.test #"^WARNING: " s))

(defn- determine-output-type
  "Returns the type of line output from the compiler.
   nil if we do not recognize the line or don't care what it is"
  [s]
  (cond
    (start-error-line? s) :start-error
    (end-error-line? s) :end-error
    (success-line? s) :success
    (warning-line? s) :warning
    (start-line? s) :start
    :else nil))

(defn- extract-time-from-success-msg [s]
  (-> s
    (replace #"^.+ in " "")
    (replace #" seconds.+$" "")
    float))



;; TODO: need to collect a list of all the possible error messages
;; https://github.com/oakmac/cljsbuild-ui/issues/3
;; TODO: if the error contains column and line information, we should extract
;; that out of the file and show the user

; (defn- error-msg-type [s]
;   (cond
;     (= 1 2) :map-literal

;     :else nil))

(defn- extract-error-msg [s]
  (-> s
    (replace #"\[\d\dm" "") ;; remove bash color coding characters
    (replace #"\s+" " ") ;; remove consecutive whitespace
    (replace #"^.*Caused by:" "")
    (replace #"^.*clojure.lang.ExceptionInfo: " "")
    (replace #" at clojure.core.*$" "")
    ))

(defn- clean-warning-line [s]
  (-> s
    (replace "WARNING: " "")
    trim))

(defn- extract-warning-msgs [s]
  (->> s
    split-lines
    (map clean-warning-line)
    (into [])))

(defn- on-console-output
  "This function gets called with chunks of text from the compiler console output.
   It parses them using regex and puts the results onto a core.async channel."
  [text1 c error-msg inside-error?]
  (let [text2 (trim text1)
        output-type (determine-output-type text2)]

    (js-log "raw output:")
    (js-log text2)
    (if output-type
      (js-log (str "### output type: " output-type)))
    (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")

    ;; TODO: need to close the error sequence when we receive nothing for some
    ;; period of time inside of one

    ;; close the error sequence if we catch any signal
    (when (and output-type @inside-error?)
      (reset! inside-error? false)
      (put! c [:error (extract-error-msg @error-msg)]))

    ;; concatenate error message
    (when @inside-error?
      (swap! error-msg str text1))

    ;; start error signal
    (when (= output-type :start-error)
      (reset! inside-error? true)
      (reset! error-msg text2)
      ;; send an "end error" signal 25ms from start of the error
      (js/setTimeout
        #(on-console-output end-error-msg c error-msg inside-error?) 25))

    ;; start compiling signal
    (when (= output-type :start)
      (put! c [:start (extract-target-from-start-msg text2)]))

    ;; compilation success
    (when (= output-type :success)
      (put! c [:success (extract-time-from-success-msg text2)]))

    ;; warnings
    (when (= output-type :warning)
      (put! c [:warning (extract-warning-msgs text2)]))))

(defn- on-close-child [c]
  (put! c [:finished])
  (close! c))

;;------------------------------------------------------------------------------
;; Helper
;;------------------------------------------------------------------------------

(defn- project-file->cwd [f]
  (replace f #"project\.clj$" ""))

(defn- project-file? [f]
  (.test #"project\.clj$" f))

;; TODO: this function needs a better name
;; also we should probably do some checking on valid cwd format
(defn- convert-cwd [cwd]
  (if (project-file? cwd)
    (project-file->cwd cwd)
    cwd))

;; https://github.com/joyent/node/issues/2318
(defn- spawn [cmd cwd]
  (if on-windows?
    (js-spawn "cmd" (array "/c" cmd) (js-obj "cwd" cwd))
    (let [cmd-arr (split cmd #" ")]
      (js-spawn (first cmd-arr)
        (apply array (rest cmd-arr))
        (js-obj "cwd" cwd)))))

(defn- kill-auto-on-unix2 [output]
  (let [lein-pid (-> output trim int)]
    (js-exec (str "kill " lein-pid))))

;; I fought with this for hours re: trying to kill the process from node.js
;; this is hacky, but it seems to work everywhere I've tested
(defn- kill-auto-on-unix [pid]
  (let [child (js-spawn "ps"
                (array "-o" "pid" "--no-headers" "--ppid" pid))]
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stdout child) "data" kill-auto-on-unix2)))

;;------------------------------------------------------------------------------
;; Public Methods
;;------------------------------------------------------------------------------

;; TODO: need to kill any "auto" process when they exit the app
(def auto-pids (atom {}))

(defn start-auto
  "Start auto-compile. This function returns a core.async channel."
  [prj-key bld-ids]
  (let [c (chan)
        child (spawn "lein cljsbuild auto" (convert-cwd prj-key))
        error-msg (atom "")
        inside-error? (atom false)]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data" #(on-console-output % c error-msg inside-error?))
    (.on (.-stdout child) "data" #(on-console-output % c error-msg inside-error?))
    (.on child "close" #(on-close-child c))

    ;; save the child pid
    (swap! auto-pids assoc prj-key (.-pid child))

    ;; return the channel
    c))

(defn stop-auto
  "Kill an auto-compile process."
  [prj-key]
  (let [main-pid (get @auto-pids prj-key)]
    (if on-windows?
      (js-exec (str "taskkill /pid " main-pid " /T /F"))
      (kill-auto-on-unix main-pid))

    ;; remove the pid from the atom
    (swap! auto-pids dissoc prj-key)))

(defn build-once
  "Start the build once process. This function returns a core.async channel
   that receives the status of the build.
   The channel is closed when the build is finished."
  [prj-key bld-ids]
  (let [c (chan)
        child (spawn "lein cljsbuild once" (convert-cwd prj-key))
        error-msg (atom "")
        inside-error? (atom false)]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data" #(on-console-output % c error-msg inside-error?))
    (.on (.-stdout child) "data" #(on-console-output % c error-msg inside-error?))
    (.on child "close" #(on-close-child c))
    ;; return the channel
    c))

;; TODO: capture better error results from this
(defn clean [cwd success-fn error-fn]
  (js-exec "lein cljsbuild clean"
    (js-obj "cwd" (convert-cwd cwd))
    (fn [err _stdout _stderr]
      (if err
        (error-fn)
        (success-fn)))))













;; NOTE: sublime text syntax highlighting chokes on this function, so I put it
;; down here at the bottom so it doesn't mess up the rest of the file

(defn- extract-target-from-start-msg [s]
  (-> s
    (replace #"^.*Compiling \"" "")
    (replace #"\".+$" "")))