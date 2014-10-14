(ns cljsbuild-ui.exec
  (:require
    [clojure.string :refer [replace split-lines trim]]
    [cljs.core.async :refer [chan close! put!]]
    [cljsbuild-ui.util :refer [log js-log uuid]]))

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

(defn- determine-output-type
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
  [text1 c]
  (let [text2 (trim text1)
        output-type (determine-output-type text2)]
    (cond
      (= output-type :error)
        (let [error-msg (extract-error-msg text2)]
          (put! c [:error error-msg]))
      (= output-type :start)
        (let [target (extract-target-from-start-msg text2)]
          (put! c [:start target]))
      (= output-type :success)
        (let [compile-time (extract-time-from-success-msg text2)]
          (put! c [:success compile-time]))
      (= output-type :warning)
        (let [warning-msgs (extract-warning-msgs text2)]
          (put! c [:warning warning-msgs]))
      :else nil)))

(defn- on-close-child [c]
  (put! c [:finished])
  (close! c))

;;------------------------------------------------------------------------------
;; Helper
;;------------------------------------------------------------------------------

(defn- project-file->cwd [f]
  (replace f #"project\.clj$" ""))

(defn- project-file? [f]
  (= -1 (.indexOf f #"project\.clj$")))

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

(defn build-once
  "Start the build once process. This function returns a core.async channel
   that receives the status of the build.
   The channel is closed when the build is finished."
  [project-key blds]
  (let [c (chan)
        child (spawn "lein"
                (array "cljsbuild" "once")
                (js-obj "cwd" (convert-cwd project-key)))]
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data" #(on-console-output % c))
    (.on (.-stdout child) "data" #(on-console-output % c))
    (.on child "close" #(on-close-child c))
    ;; return the channel
    c))

;; TODO: capture better error results from this
(defn clean [cwd success-fn error-fn]
  (exec "lein cljsbuild clean"
    (js-obj "cwd" (convert-cwd cwd))
    (fn [err _stdout _stderr]
      (if err
        (error-fn)
        (success-fn)))))













;; NOTE: sublime text syntax highlighting chokes on this function, so I put it
;; down here at the bottom so it doesn't mess up the rest of the file

(defn- extract-target-from-start-msg [s]
  (-> s
    (replace "Compiling \"" "")
    (replace #"\".+$" "")))