(ns cljsbuild-ui.projects
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [clojure.string :refer [replace]]
    [cljs.reader :refer [read-string]]
    [cljsbuild-ui.cljsbuild.config :refer [extract-options]]
    [cljsbuild-ui.exec :refer [get-cljsbuild-with-profiles]]
    [cljsbuild-ui.util :refer [path-join
                               path-dirname]]))

(def fs (js/require "fs"))

;;------------------------------------------------------------------------------
;; Project Parsing
;;------------------------------------------------------------------------------

(defn- add-default-id-to-build
  [i build]
  (if-not (:id build)
    (assoc build :id (str "build " i))
    build))

(defn normalize-cljsbuild-opts
  [opts]
  (let [opts (extract-options {:cljsbuild opts})
        builds (->> (:builds opts)
                    (map-indexed add-default-id-to-build))]
    (assoc opts :builds builds)))

(defn- parse-project-file
  "Parse the project file without considering profiles."
  [contents filename]
  (let [contents (replace contents "#(" "(") ;; prevent "Could not find tag parser for" error
        prj1 (read-string contents)
        project (apply hash-map (drop 3 prj1))
        cljsbuild (when-let [opts (:cljsbuild project)]
                    (normalize-cljsbuild-opts opts))]
    (assoc project
           :cljsbuild cljsbuild
           :filename filename
           :name (name (nth prj1 1))
           :version (nth prj1 2))))

(defn- fix-project-with-profiles
  "Correct the given project file with cljsbuild options from profiles."
  [project]
  (go
    (let [filename (:filename project)
          path (path-dirname filename)
          cljsbuild (<! (get-cljsbuild-with-profiles path))
          cljsbuild2 (normalize-cljsbuild-opts cljsbuild)]
      (assoc project :cljsbuild cljsbuild2))))

;; TODO:
;; - Need to handle files listed in projects.json that no longer exist on disk (GitHub Issue #45)
;;   (ie: did you remove this file?)
;; - need to do some quick validation of project.clj
;;   (ie: does it have :cljsbuild?)
(defn load-project-file [filename]
  (go
    (let [file-contents (.readFileSync fs filename (js-obj "encoding" "utf8"))
          project (parse-project-file file-contents filename)]
      (if (:cljsbuild project)
        project
        (<! (fix-project-with-profiles project))))))

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
