(ns cuttle.util)

;; https://github.com/markmandel/while-let
(defmacro while-let
  "Repeatedly executes body while test expression is true, evaluating the body
   with binding-form bound to the value of test."
  [bindings & body]
  (let [form (first bindings) test (second bindings)]
    `(loop [~form ~test]
       (when ~form
         ~@body
         (recur ~test)))))
