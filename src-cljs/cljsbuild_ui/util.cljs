(ns cljsbuild-ui.util)

(defn log
  "Log a Clojure thing."
  [clj-thing]
  (js/console.log (pr-str clj-thing)))

(defn js-log
  "Log a JavaScript thing."
  [js-thing]
  (js/console.log js-thing))

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