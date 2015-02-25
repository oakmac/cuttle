(ns cuttle.projects
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<! put! close! chan]]
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [replace]]
    [cuttle.cljsbuild.config :refer [extract-options]]
    [cuttle.exec :refer [get-cljsbuild-with-profiles]]
    [cuttle.log :refer [log-info]]
    [cuttle.util :refer [file-exists? js-log log path-join path-dirname
                         try-read-string not-empty?]]))

(def fs (js/require "fs"))

(declare write-workspace!)

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
  (log-info "parsing project:" filename)
  (let [contents (replace contents "#(" "(") ;; prevent "Could not find tag parser for" error
        prj1 (try-read-string contents)]
        (if (contains? prj1 :error)
          ;; then
          (assoc prj1
                 :filename filename
                 :name filename
                 :cljsbuild {})
          ;; else
          (let [project (apply hash-map (drop 3 prj1))
                cljsbuild (when-let [opts (:cljsbuild project)]
                            (normalize-cljsbuild-opts opts))]
            (assoc project
                   :cljsbuild cljsbuild
                   :filename filename
                   :name (name (nth prj1 1))
                   :version (nth prj1 2))))))

;; TODO: Refactor this
(defn- check-builds
  [prj]
  (let [builds (:builds (:cljsbuild prj))
        out-dirs (map #(get-in % [:compiler :output-dir]) builds)
        out-paths (map #(get-in % [:compiler :output-to]) builds)]
    (if (and (not-empty? out-dirs) (not-empty? out-paths))
      ;; then
      (if-not (and (apply distinct? out-dirs)
                 (apply distinct? out-paths))
        ;; then
        (assoc prj
               :error (str "Error: "
                           "output-to and output-dir must be distinct "
                           "in the builds profile."))
        ;; else
        prj)
      ;; else
      prj)))

(defn- fix-project-with-profiles
  "Correct the given project file with cljsbuild options from profiles."
  [project]
  (log-info "parsing project with :dev profile:" (:filename project))
  (go
    (let [filename (:filename project)
          path (path-dirname filename)
          cljsbuild (<! (get-cljsbuild-with-profiles path))
          cljsbuild2 (normalize-cljsbuild-opts cljsbuild)]
            (assoc project :cljsbuild cljsbuild2))))

;; TODO:
;; - need to do some quick validation of project.clj
;;   (ie: does it have :cljsbuild?)
(defn load-project-file [filename]
  (let [c (chan)]
    (go
      (let [file-contents (.readFileSync fs filename (js-obj "encoding" "utf8"))
            project (parse-project-file file-contents filename)]
        (put! c (check-builds project))
        (when-not (:cljsbuild project)
          (put! c (<! (check-builds (fix-project-with-profiles project)))))
        (close! c)))
    c))

;;------------------------------------------------------------------------------
;; Project Workspace Initialization
;;------------------------------------------------------------------------------

(def workspace-filename)

(defn- set-workspace-filename!
  [app-data-path]
  (set! workspace-filename (path-join app-data-path "projects.json")))

(defn- create-default-projects-file!
  [app-data-path projects-file]
  (when-not (file-exists? app-data-path)
    (.mkdirSync fs app-data-path))
  (.writeFileSync fs projects-file
    (.stringify js/JSON (array) nil 2)
    (js-obj "encoding" "utf8")))

(defn load-workspace!
  [app-data-path]
  (set-workspace-filename! app-data-path)
  ;; TODO: need to do some quick validation on projects.json format here
  (when-not (file-exists? workspace-filename)
    (create-default-projects-file! app-data-path workspace-filename))
  (let [filenames1 (js->clj (js/require workspace-filename))
        filenames2 (vec (filter file-exists? filenames1))]

    ;; re-write projects.json if it contains a project.clj file that no longer exists
    ;; TODO: add some UX around this to inform the user that this has happened, GitHub Issue #45
    (when (not= filenames1 filenames2)
      (write-workspace! filenames2))

    filenames2))

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
