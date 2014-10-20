(ns cljsbuild-ui.exec
  (:require
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [join replace split-lines split trim]]
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

(defn- extract-line-info [s]
  (-> s
    (replace #".+\{:column " "{:column ")
    (replace #"reader-exception\}.+$" "reader-exception}")
    read-string))

(defn- error-has-line-info? [s]
  (and (.test #"\{\:column" s)
       (.test #" \:line " s)
       (.test #"\:reader-exception\}" s)))

(defn- default-extract-error-msg [s]
  (-> s
    (replace #"^.*Caused by:" "")
    (replace #"^.*clojure.lang.ExceptionInfo: " "")
    (replace #" at clojure.core.*$" "")
    ))

(defn- extract-eof-msg [s]
  (-> s
    (replace #"^.+EOF while reading" "EOF while reading")
    (replace #" core.clj.+$" "")))

(defn- clean-error-text
  "Remove bash color coding characters."
  [s]
  (-> s
    (replace #"\[\dm" "")
    (replace #"\[\d\dm" "")))

(defn- extract-unmatched-delimiter [s]
  (-> s
    (replace #"^.+Unmatched delimiter" "Unmatched delimiter")
    ;(replace #"^.+Unmatched delimiter" "Unmatched delimiter")
    ))

(defn- extract-error-msg [type cleaned-error]
  (case type
    :unmatched-delimiter (extract-unmatched-delimiter cleaned-error)
    :map-literal "map literal error msg!"
    :eof (extract-eof-msg cleaned-error)
    (default-extract-error-msg cleaned-error)))

(defn- map-literal? [s]
  (.test #"Map literal must contain an even" s))

(defn- unmatched-delimiter? [s]
  (.test #"Unmatched delimiter" s))

(defn- eof? [s]
  (.test #"EOF while reading" s))

(defn- determine-error-type [s]
  (cond
    (eof? s) :eof
    (unmatched-delimiter? s) :unmatched-delimiter
    (map-literal? s) :map-literal
    ;; TODO: more error types go here
    :else nil))

(defn- red-line? [s]
  (.test #"\[31m" s))

(defn- extract-error-msg [full-error-txt]
  (->> full-error-txt
    split-lines
    (filter red-line?)
    (map clean-error-text)))

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
  [raw-output c inside-error? err-msg-buffer err-msg-timeout]
  (let [trimmed-output (trim raw-output)
        output-type (determine-output-type trimmed-output)]

    (js-log "raw output:")
    (js-log trimmed-output)
    (if output-type
      (js-log (str "### output type: " output-type)))
    (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")

    ;; close the error sequence if we catch any signal
    (when (and output-type @inside-error?)
      (js/clearTimeout @err-msg-timeout)
      (reset! inside-error? false)
      (put! c [:error (extract-error-msg @err-msg-buffer)]))

    ;; concatenate error message
    (when @inside-error?
      (swap! err-msg-buffer str raw-output "\n"))

    ;; start error signal
    (when (= output-type :start-error)
      (reset! inside-error? true)
      (reset! err-msg-buffer raw-output)

      ;; send an "end error" signal 25ms from start of the error
      (let [t (js/setTimeout
                #(on-console-output end-error-msg c inside-error?
                   err-msg-buffer err-msg-timeout)
                25)]
        (reset! err-msg-timeout t)))

    ;; start compiling signal
    (when (= output-type :start)
      (put! c [:start (extract-target-from-start-msg trimmed-output)]))

    ;; compilation success
    (when (= output-type :success)
      (put! c [:success (extract-time-from-success-msg trimmed-output)]))

    ;; warnings
    (when (= output-type :warning)
      (put! c [:warning (extract-warning-msgs trimmed-output)]))))

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
        inside-error? (atom false)
        err-msg-buffer (atom "")
        err-msg-timeout (atom nil)]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer err-msg-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer err-msg-timeout))
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
        inside-error? (atom false)
        err-msg-buffer (atom "")
        err-msg-timeout nil]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer err-msg-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer err-msg-timeout))
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