(ns cljsbuild-ui.util)

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def js-path (js/require "path"))

;;------------------------------------------------------------------------------
;; Util Functions
;;------------------------------------------------------------------------------

(defn path-join
  "Create a path string from given args with OS-specific path separators"
  [& args]
  (let [path-join (aget js-path "join")]
    (apply path-join args)))

(def on-windows?
  (.test #"^win" js/process.platform))

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
