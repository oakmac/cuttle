(ns cljsbuild-ui.projects
  (:require
    [clojure.string :refer [replace]]
    [cljs.reader :refer [read-string]]
    [cljsbuild-ui.cljsbuild.config :refer [extract-options]]
    [cljsbuild-ui.util :refer [path-join]]))

(def fs (js/require "fs"))

;;------------------------------------------------------------------------------
;; Project Parsing
;;------------------------------------------------------------------------------

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
(defn load-project-file [filename]
  (let [file-contents (.readFileSync fs filename (js-obj "encoding" "utf8"))]
    (parse-project-file file-contents filename)))

;;------------------------------------------------------------------------------
;; Project Workspace Initialization
;;------------------------------------------------------------------------------

(def workspace-filename)

(defn- set-workspace-filename!
  [app-data-path]
  (set! workspace-filename (path-join app-data-path "projects.json")))

(defn- create-default-projects-file!
  [app-data-path projects-file]
  (.mkdirSync fs app-data-path)
  (.writeFileSync fs projects-file
    (.stringify js/JSON (array) nil 2)
    (js-obj "encoding" "utf8")))

(defn load-workspace!
  [app-data-path]
  (set-workspace-filename! app-data-path)
  ;; TODO: need to do some quick validation on projects.json format here
  (when-not (.existsSync fs workspace-filename)
    (create-default-projects-file! app-data-path workspace-filename))
  (let [filenames (js->clj (js/require workspace-filename))]
    filenames))

;;------------------------------------------------------------------------------
;; Project Workspace Modification
;;------------------------------------------------------------------------------

(defn- read-workspace
  []
  (let [content (.readFileSync fs workspace-filename "utf8")
        js-projects (.parse js/JSON content)]
    (js->clj js-projects)))

(defn- write-workspace!
  [projects]
  (let [js-projects (clj->js projects)
        content (.stringify js/JSON js-projects nil 2)
        options #js {:encoding "utf8"}]
    (.writeFileSync fs workspace-filename content options)))

(defn add-to-workspace!
  [filename]
  (let [projects (read-workspace)
        in-projects? (get (into #{} projects) filename)
        should-add? (not in-projects?)]
    (when should-add?
      (write-workspace! (conj projects filename)))
    should-add?))

(defn remove-from-workspace!
  [filename]
  (let [projects (read-workspace)
        in-projects? (get (into #{} projects) filename)]
    (when in-projects?
      (write-workspace! (remove #{filename} projects)))))
