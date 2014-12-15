(ns cljsbuild-ui.core
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [cljsbuild-ui.pages.main :as main-page]
    [cljsbuild-ui.projects :refer [load-workspace!]]
    [cljsbuild-ui.exec :refer [add-lein-profile!]]))

(enable-console-print!)

(def ipc (js/require "ipc"))

(defn- on-load
  [app-data-path]
  (go
    (let [filenames (load-workspace! app-data-path)]
      (<! (add-lein-profile!))
      (main-page/init! filenames))))

;; You can find the atom-shell entry point at "app/app.js".
;; It sends the OS-normalized app data path to this event,
;; effectively "global app init" for the webpage.
(.on ipc "config-file-location" on-load)
