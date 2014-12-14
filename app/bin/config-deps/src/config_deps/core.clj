(ns config-deps.core
  (:require
    [clojure.java.io :as io]
    [leiningen.core.user :refer [leiningen-home]]
    [me.raynes.fs :as fs]
    [rewrite-clj.zip :as z]))

(def plugins
  [])

(def dependencies
  [['lein-pprint "1.1.1"]
   ;; ['lein-cljsbuild-ui "0.1.0"]
   ])

(def default-user-prof
  {:plugins []
   :dependencies []})

(defn create-default-profile!
  [path]
  (spit path "{}"))

(defn get-profile-path
  []
  (.getPath (io/file (leiningen-home) "profiles.clj")))

(defn get-profile-zipper
  []
  (let [path (get-profile-path)
        prof-str (slurp path)]
    (z/of-string prof-str)))

(defn add-to-profile!
  [path]
  (let [prof-z (get-profile-zipper)
        prof-s (z/sexpr prof-z)
        prof-z (if-not (:user prof-s)
                 (z/assoc prof-z :user default-user-prof)
                 prof-z)
        user-z (z/get prof-z :user)
        user-s (z/sexpr user-z)]

    ;; TODO: Which profile should we be using?
    ;;       To avoid dependency collisions, we should probably create a cljsbuild-ui profile.
    ;;       
    ;; TODO: at this point, we can use code from 'lein-plz' to insert deps

    nil))

(defn -main
  [& args]
  (let [path (get-profile-path)]

    (when-not (fs/exists? path)
      (create-default-profile! path))
    
    (add-to-profile!)))
