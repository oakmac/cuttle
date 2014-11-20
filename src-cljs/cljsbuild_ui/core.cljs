(ns cljsbuild-ui.core
  (:require
    [cljsbuild-ui.pages.main]
    [cljsbuild-ui.projects :as projects]
    ))

(def ipc (js/require "ipc"))

(defn- on-load-projects
  [filenames]
  (cljsbuild-ui.pages.main/init! filenames))

;; TODO: need to do some quick validation on projects.json format here
(defn- receive-app-data-path
  [app-data-path]
  (projects/load-workspace app-data-path on-load-projects))

;; You can find the atom-shell entry point at "app/app.js".
;; It sends the OS-normalized app data path to this event,
;; effectively "global app init" for the webpage.
(.on ipc "config-file-location" receive-app-data-path)
