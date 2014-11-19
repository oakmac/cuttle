(ns cljsbuild-ui.exec
  (:require
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [join replace split-lines split trim]]
    [cljs.core.async :refer [chan close! put!]]
    [cljsbuild-ui.config :refer [config]]
    [cljsbuild-ui.util :refer [js-log log on-windows? path-join uuid]]))

(declare extract-target-from-start-msg)

(defn lein-path
  "Get path to our packaged leiningen script."
  []
  (let [dir (aget js/global "__dirname")
        exe (if on-windows? "lein.bat" "lein")]
    (path-join dir "bin" exe)))

(defn lein
  "Make lein command string"
  [args]
  (str (lein-path) " " args))

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def child-proc (js/require "child_process"))
(def fs (js/require "fs.extra"))
(def js-exec (aget child-proc "exec"))
(def js-spawn (aget child-proc "spawn"))

;;------------------------------------------------------------------------------
;; Determine Compiler Output Line Type
;;------------------------------------------------------------------------------

;; NOTE: this probably belongs in it's own library or emitted as EDN from the
;; compiler itself
;; we can get by with regex and duct tape for now ;)

(defn- red-line? [s]
  (.test #"\[31m" s))

(defn- error-line? [s]
  (or (red-line? s)
      (.test #"clojure\.lang\.ExceptionInfo" s)))

(def ^:private stopped-signal (str "*** OUTPUT STOPPED *** " (uuid)))

(defn- end-line? [s]
  (or (.test #"Subprocess failed" s)
      (= s stopped-signal)))

(defn- success-line? [s]
  (and (.test #"Successfully compiled" s)
       (.test #"seconds\." s)))

(defn- warning-line? [s]
  (.test #"^WARNING: " s))

(defn- start-line? [s]
  (and (.test #"Compiling " s)
       (.test #"]\.\.\.$" s)))

(defn- determine-line-type
  "Returns the type of line output from the compiler.
   nil if we do not recognize the line or don't care what it is"
  [s]
  (cond
    (error-line? s) :error
    (end-line? s) :end-output
    (success-line? s) :success
    (warning-line? s) :warning
    (start-line? s) :start
    :else nil))

;;------------------------------------------------------------------------------
;; Extract Info From Lines
;;------------------------------------------------------------------------------

(defn- extract-time-from-success-msg [s]
  (-> s
    (replace #"^.+ in " "")
    (replace #" seconds.+$" "")
    float))

;; TODO: need to collect a list of all the possible error messages
;; https://github.com/oakmac/cljsbuild-ui/issues/3

;; TODO: if the error contains column and line information, we should extract
;; that out of the file and show the user

(defn- extract-error-info [s]
  (-> s
    (replace #"^.*\{:column" "{:column")
    (replace #"reader-exception\}.*$" "reader-exception}")
    read-string))

(defn- error-has-line-info? [s]
  (and (.test #"\{\:column" s)
       (.test #" \:line " s)
       (.test #"\:reader-exception\}" s)))

; (defn- default-extract-error-msg [s]
;   (-> s
;     (replace #"^.*Caused by:" "")
;     (replace #"^.*clojure.lang.ExceptionInfo: " "")
;     (replace #" at clojure.core.*$" "")))

(defn- clean-error-text
  "Remove bash color coding characters."
  [s]
  (-> s
    (replace #"\[\dm" "")
    (replace #"\[\d\dm" "")))

; (defn- extract-error-msg [full-error-txt]
;   (->> full-error-txt
;     split-lines
;     (filter red-line?)
;     (map clean-error-text)))

(defn- clean-warning-line [s]
  (-> s
    (replace "WARNING: " "")
    trim))

(defn- extract-warning-msgs [s]
  (->> s
    split-lines
    (map clean-warning-line)
    (into [])))

(defn- clean-line
  "Clean bash escape characters from a console output line."
  [s]
  (-> s
    (replace #"\033" "")    ;; remove escape characters
    (replace #"\[\dm" "")   ;; remove color codes
    (replace #"\[\d\dm" "")
    trim))

(defn- on-console-line
  "Handles each line from the compiler console output.
   It parses them using regex and puts the results onto a core.async channel."
  [raw-line return-chan inside-error? err-msg-buffer]
  (let [line-type (determine-line-type raw-line)
        cleaned-line (clean-line raw-line)]

    (when (:log-compiler-output config)
      (js-log raw-line)
      (js-log cleaned-line)
      (when line-type
        (log (str "##### line type: " line-type)))
      (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"))

    ;; start an error sequence
    (when (and (not @inside-error?)
               (= line-type :error))
      (reset! inside-error? true)
      (reset! err-msg-buffer []))

    ;; collect error messages
    (when (and @inside-error?
               (= line-type :error))
      (swap! err-msg-buffer conj cleaned-line))

    ;; close an error sequence
    (when (and @inside-error?
               line-type
               (not= line-type :error))
      (reset! inside-error? false)
      (put! return-chan [:error @err-msg-buffer]))

    ;; start compiling signal
    (when (= line-type :start)
      (put! return-chan [:start (extract-target-from-start-msg raw-line)]))

    ;; compilation success
    (when (= line-type :success)
      (put! return-chan [:success (extract-time-from-success-msg raw-line)]))

    ;; warnings
    (when (= line-type :warning)
      (put! return-chan [:warning (extract-warning-msgs raw-line)]))))

(defn- on-console-output
  "This function gets called with chunks of text from the compiler console output.
   We split it on newlines and then handle each line individually."
  [raw-output return-chan inside-error? err-msg-buffer stopped-output-timeout]

  ; (js-log "raw output:")
  ; (js-log raw-output)
  ; (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")

  ;; clear timeout whenever we receive a new chunk
  (js/clearTimeout @stopped-output-timeout)

  ;; parse every line of the output
  (doall
    (map
      #(on-console-line % return-chan inside-error? err-msg-buffer)
      (split-lines raw-output)))

  ;; Set a timeout to indicate the output has stopped 50ms after the last chunk
  ;; is received.
  ;; NOTE: this is used to determine when the output has "stopped" so we can
  ;; close the error sequence
  (let [t (js/setTimeout
            #(on-console-line stopped-signal return-chan inside-error? err-msg-buffer)
            50)]
    (reset! stopped-output-timeout t)))

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
;; this feels hacky, but it seems to work
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

;; TODO: need to combine the start-auto and build-once functions

(defn start-auto
  "Start auto-compile. This function returns a core.async channel."
  [prj-key bld-ids]
  (let [c (chan)
        lein-cmd (str "cljsbuild auto " (join " " bld-ids))
        child (spawn (lein lein-cmd) (convert-cwd prj-key))
        inside-error? (atom false)
        err-msg-buffer (atom "")
        stopped-output-timeout (atom nil)]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
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
        lein-cmd (str "cljsbuild once " (join " " bld-ids))
        child (spawn (lein lein-cmd) (convert-cwd prj-key))
        inside-error? (atom false)
        err-msg-buffer (atom "")
        stopped-output-timeout (atom nil)]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on child "close" #(on-close-child c))
    ;; return the channel
    c))

;; TODO: "target" here is probably configurable in build; need to look closer
;; at Leiningen's code
(defn clean-build! [prj-key bld]
  (let [cwd (convert-cwd prj-key)
        output-dir (-> bld :compiler :output-dir)
        output-dir-full (str cwd "target" output-dir)
        output-to (-> bld :compiler :output-to)
        output-to-full (str cwd output-to)]
    (when (and output-dir (.existsSync fs output-dir-full))
      (.rmrfSync fs output-dir-full))
    (when (and output-to (.existsSync fs output-to-full))
      (.unlinkSync fs output-to-full))))











;; NOTE: sublime text syntax highlighting chokes on this function, so I put it
;; down here at the bottom so it doesn't mess up the rest of the file

(defn- extract-target-from-start-msg [s]
  (-> s
    (replace #"^Compiling \"" "")
    (replace #"\".+$" "")))
