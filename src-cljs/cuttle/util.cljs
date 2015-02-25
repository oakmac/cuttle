(ns cuttle.util
  (:require [cljs.reader :refer [read-string]]))

;;------------------------------------------------------------------------------
;; Node Modules
;;------------------------------------------------------------------------------

(def fs (js/require "fs"))
(def js-path (js/require "path"))

;;------------------------------------------------------------------------------
;; Version
;;------------------------------------------------------------------------------

(def package-json (js/require "./package.json"))
(def current-version (aget package-json "version"))
(def build-date (aget package-json "build-date"))
(def build-commit (aget package-json "build-commit"))

;;------------------------------------------------------------------------------
;; Util Functions
;;------------------------------------------------------------------------------
(defn not-empty?
  [col]
  (some? (not-empty col)))

(defn try-read-string
  [contents]
  (try
    (read-string contents)
    (catch :default e
         {:error (str e ". Failed to read project.clj." )})))

(defn path-join
  "Create a path string from given args with OS-specific path separators"
  [& args]
  (let [path-join (aget js-path "join")]
    (apply path-join args)))

(defn path-dirname
  [p]
  (let [dirname (aget js-path "dirname")]
    (dirname p)))

(def on-windows?
  (.test #"^win" js/process.platform))

(def on-mac?
  (.test #"^darwin" js/process.platform))

(def on-linux?
  (.test #"^linux" js/process.platform))

(def homedir (aget js/process "env" (if on-windows? "USERPROFILE" "HOME")))

(defn log
  "Log a Clojure thing."
  [clj-thing]
  (js/console.log (pr-str clj-thing)))

(defn js-log
  "Log a JavaScript thing."
  [js-thing]
  (js/console.log js-thing))

(defn now
  "Returns the current UNIX timestamp in seconds."
  []
  (.unix (js/moment)))

(defn date-format [ts frmt-str]
  (.format (js/moment ts "X") frmt-str))

(defn uuid []
  "Create a UUID."
  []
  (apply
   str
   (map
    (fn [x]
      (if (= x \0)
        (.toString (bit-or (* 16 (.random js/Math)) 0) 16)
        x))
    "00000000-0000-4000-0000-000000000000")))

;;------------------------------------------------------------------------------
;; File Util
;;------------------------------------------------------------------------------

(def windows-bin-dir "C:\\cuttle-bin\\")

(defn file-exists? [filename]
  (.existsSync fs filename))

(defn write-file-async!
  "Useful when we want to write a file asynchronously and do not care when it
   is finished."
  [filename contents]
  (.writeFile fs filename contents))
