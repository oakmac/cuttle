(ns cuttle.core
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [hiccups.core :as hiccups])
  (:require
    [cljs.core.async :refer [<!]]
    [cuttle.config :refer [app-data-path]]
    [cuttle.dom :refer [by-id hide-el! set-html! show-el!]]
    [cuttle.exec :refer [add-lein-profile! correct-java-installed?
                         kill-all-leiningen-instances! lein]]
    [cuttle.log :refer [log-info]]
    [cuttle.main-page :as main-page]
    [cuttle.projects :refer [load-workspace!]]
    [cuttle.util :refer [on-windows? path-join windows-bin-dir]]
    hiccups.runtime))

(enable-console-print!)

(def fs (js/require "fs-extra"))
(def ipc (js/require "ipc"))
(def shell (js/require "shell"))

;;------------------------------------------------------------------------------
;; Check for our lein files on Windows
;;------------------------------------------------------------------------------

(defn- lein-files-in-place? []
  ;; TODO: write me
  ;; we shouldn't have to copy the files from app/bin to c:\cuttle-bin if they
  ;; are already there
  false)

;; NOTE: this function is the poster-child for how go blocks are cleaner than
;; nested callbacks
;; don't judge me please; I was sick when I wrote this
;; TODO: we need to handle when there is a permission error here
(defn- copy-lein-files! [next-fn]
  (log-info "copying lein.bat to our windows bin dir")
  (.ensureDir fs windows-bin-dir (fn []
    (.copy fs (path-join js/__dirname "bin" "lein.bat")
              (str windows-bin-dir "lein.bat")
              (fn []
                (.copy fs (path-join js/__dirname "bin" "lein.jar")
                          (str windows-bin-dir "lein.jar")
                          next-fn))))))

;;------------------------------------------------------------------------------
;; Loading / Shutting Down Pages
;;------------------------------------------------------------------------------

(hiccups/defhtml shutdown-page []
  [:div.loading-6792e
    [:i.fa.fa-cog.fa-spin]
    "Shutting Down"])

(defn- show-shutting-down-page! []
  (set-html! "shutdownPage" (shutdown-page))
  (hide-el! "loadingPage")
  (hide-el! "mainPage")
  (show-el! "shutdownPage"))

(hiccups/defhtml loading-page []
  [:div.loading-6792e
    [:i.fa.fa-cog.fa-spin]
    "Loading"])

(defn- show-loading-page! []
  (set-html! "loadingPage" (loading-page))
  (hide-el! "shutdownPage")
  (hide-el! "mainPage")
  (show-el! "loadingPage"))

(def jre-url
  "http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html")

(hiccups/defhtml download-jre-page []
  [:div.jre-5d930
    "The ClojureScript compiler requires " [:a#jre-link "Java SE Runtime >= 7"] "."
    [:br]
    "Please install, then restart this tool."])

(defn- show-download-jre-page! []
  (set-html! "loadingPage" (download-jre-page))
  (aset (by-id "jre-link") "onclick" #(.openExternal shell jre-url))
  (hide-el! "shutdownPage")
  (hide-el! "mainPage")
  (show-el! "loadingPage"))

;; initialize to the loading page
(show-loading-page!)

;;------------------------------------------------------------------------------
;; Shutdown Signal
;;------------------------------------------------------------------------------

(defn- on-shutdown []
  (log-info "attempting to shutdown")
  (go
    (show-shutting-down-page!)
    (<! (kill-all-leiningen-instances!))
    (log-info "done killing leiningen instances")
    (.send ipc "shutdown-for-real")))

(.on ipc "shutdown-attempt" on-shutdown)

;;------------------------------------------------------------------------------
;; Client Init
;;------------------------------------------------------------------------------

(defn- global-init2!
  [new-app-data-path]
  (set! app-data-path new-app-data-path)
  (go
    (if-not (<! (correct-java-installed?))
      (show-download-jre-page!)
      (let [filenames (load-workspace! app-data-path)]
        (<! (add-lein-profile!))
        (main-page/init! filenames)))))

(defn- global-init! [new-app-data-path]
  (log-info "starting app; received" new-app-data-path)
  (log-info "using lein at" (lein ""))
  (if on-windows?
    (copy-lein-files! (partial global-init2! new-app-data-path))
    (global-init2! new-app-data-path)))

;; You can find the atom-shell entry point at "app/app.js".
;; It sends the OS-normalized app data path to this event,
;; effectively "global app init" for the webpage.
(.on ipc "config-file-location" global-init!)
