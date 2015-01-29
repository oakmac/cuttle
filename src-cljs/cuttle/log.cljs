(ns cuttle.log
  (:require
    [clojure.string :refer [join]]))

(def ipc (js/require "ipc"))

(defn log-info  [& args] (.send ipc "log-info"  (join " " args)))
(defn log-warn  [& args] (.send ipc "log-warn"  (join " " args)))
(defn log-error [& args] (.send ipc "log-error" (join " " args)))
