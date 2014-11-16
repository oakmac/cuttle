(ns cljsbuild-ui.core
  (:require
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [join replace split trim]]
    [cljsbuild-ui.pages.main]
    [cljsbuild-ui.util :refer [log js-log on-windows? uuid path-join]]
    [cljsbuild-ui.cljsbuild.config :refer [extract-options]]))

(def fs (js/require "fs"))
(def ipc (js/require "ipc"))

;;------------------------------------------------------------------------------
;; Load or Create Projects File
;;------------------------------------------------------------------------------

(defn- create-default-projects-file! [app-data-path projects-file]
  (.mkdirSync fs app-data-path)
  (.writeFileSync fs projects-file
    (.stringify js/JSON (array) nil 2)
    (js-obj "encoding" "utf8")))

;; TODO: we probably need to add the name and version to our project map here
(defn- parse-project-file [s1 filename]
  (let [s2 (replace s1 "#(" "(") ;; prevent "Could not find tag parser for" error
        prj1 (read-string s2)
        project (apply hash-map (drop 3 prj1))
        cljsbuild (extract-options project)]
    (assoc project
           :cljsbuild cljsbuild
           :filename filename
           :name (name (nth prj1 1))
           :version (nth prj1 2))))

;; TODO:
;; - Need to handle files listed in projects.json that no longer exist on disk
;;   (ie: did you remove this file?)
;; - need to do some quick validation of project.clj
;;   (ie: does it have :cljsbuild?)
(defn- load-project-file [filename]
  (let [file-contents (.readFileSync fs filename (js-obj "encoding" "utf8"))]
    (parse-project-file file-contents filename)))

;; TODO: need to do some quick validation on projects.json format here
(defn- receive-app-data-path [app-data-path]
  (let [projects-file (path-join app-data-path "projects.json")]
    (log projects-file)
    (when-not (.existsSync fs projects-file)
      (create-default-projects-file! app-data-path projects-file))
    (let [prj-files (js->clj (js/require projects-file))
          projects (mapv load-project-file prj-files)]
      ;; it works!
      ;;(log (:cljsbuild (first (vals projects))))
      (cljsbuild-ui.pages.main/init! projects)
      )))

;; receive the OS-normalized app data path from app.js
;; NOTE: this event is effectively "global app init" for the webpage
(.on ipc "config-file-location" receive-app-data-path)

