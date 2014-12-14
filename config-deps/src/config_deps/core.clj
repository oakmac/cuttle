(ns config-deps.core
  (:require
    [clojure.java.io :as io]
    [leiningen.core.user :refer [leiningen-home]]
    [me.raynes.fs :as fs]
    [rewrite-clj.zip :as z]))

(def my-profile
  '{:plugins [[lein.pprint "1.1.1"]]
    :dependencies []})

(defn create-default-profile!
  [path]
  (spit path "{\n}"))

(defn get-profile-path
  []
  (.getPath (io/file (leiningen-home) "profiles.clj")))

(defn add-to-profile!
  [path]
  (let [prof-str     (slurp path)
        prof         (z/of-string prof-str)
        new-prof     (z/assoc prof :cljsbuild-ui my-profile)
        new-prof-str (with-out-str (z/print-root new-prof))]
    (spit path new-prof-str)))

(defn -main
  [& args]
  (let [path (get-profile-path)]
    (when-not (fs/exists? path)
      (create-default-profile! path))
    (add-to-profile! path)))
