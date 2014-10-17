(ns cljsbuild-ui.core
  (:require
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [join replace split]]
    [cljsbuild-ui.pages.main]
    [cljsbuild-ui.util :refer [log js-log on-windows? uuid]]))

(def fs (js/require "fs"))
(def ipc (js/require "ipc"))

;;------------------------------------------------------------------------------
;; Load or Create Projects File
;;------------------------------------------------------------------------------

(def slash (if on-windows? "\\" "/"))

(defn- create-default-projects-file! [app-data-path projects-file]
  (.mkdirSync fs app-data-path)
  (.writeFileSync fs projects-file
    (.stringify js/JSON (array) nil 2)
    (js-obj "encoding" "utf8")))

;; can't tell if this hack is horrible or brilliant
(defn- defproject->map [s1]
  (let [arr (split s1 #"\s")
        prj-name (nth arr 1)
        version (nth arr 2)
        s2 (str "{" (join " " (drop 3 arr)))]
    ; (js-log s2)
    ; (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
    ; (js-log (replace #";.*\n"))
    (-> s2
      (replace #";.+\n" "")
      (replace #"\)$" "}")
      (replace "#(" "(") ;; remove tagged literals
      )))

;; TODO: need to handle files that do not exist, can punt on this for now
;; TODO: parse the project.clj using transit here
;; TODO: need to do some quick validation of the project.clj file
(defn- load-project-file [prj-file]
  (let [file-contents (.readFileSync fs prj-file)
        ;;prj (read-string (defproject->map file-contents))
        prj (defproject->map file-contents)
        ]
    prj))

(defn- receive-app-data-path [app-data-path]
  (let [projects-file (str app-data-path slash "projects.json")]
    (when-not (.existsSync fs projects-file)
      (create-default-projects-file! app-data-path projects-file))
    (let [prj-files (js->clj (js/require projects-file))
          projects (zipmap prj-files (map load-project-file prj-files))]
      (log projects))))

;; receive the OS-normalized app data path from app.js
;; NOTE: this event is effectively "global app init" for the webpage
(.on ipc "config-file-location" receive-app-data-path)

;;------------------------------------------------------------------------------
;; Global App Init
;;------------------------------------------------------------------------------

(cljsbuild-ui.pages.main/init!)