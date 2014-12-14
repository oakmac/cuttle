(ns add-lein-profile.core
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [leiningen.core.user :refer [leiningen-home]]
    [me.raynes.fs :as fs]
    [rewrite-clj.zip :as z]
    [clojure.edn :as edn]))

(defn create-default-profile!
  [path]
  (spit path "{\n}"))

(defn get-profile-path
  []
  (.getPath (io/file (leiningen-home) "profiles.clj")))

(defn set-profile!
  [path name- content]
  (let [prof-str     (slurp path)
        prof         (z/of-string prof-str)
        new-prof     (z/assoc prof name- content)
        new-prof-str (with-out-str (z/print-root new-prof))]
    (spit path new-prof-str)))

(defn -main
  ([] (println "Usage: prog <profile-map>"))
  ([map-str]
   (let [path (get-profile-path)]
     (when-not (fs/exists? path)
       (create-default-profile! path))

     (let [prof-map (edn/read-string map-str)]
       (doseq [[name- content] prof-map]
         (set-profile! path name- content))))))
