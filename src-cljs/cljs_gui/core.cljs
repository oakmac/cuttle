(ns cljs-gui.core
  (:require
    [goog.events :as gevents]
    [quiescent :include-macros true]
    [sablono.core :as sablono :include-macros true]
    [cljs-gui.util :refer [log js-log uuid]]))

;;------------------------------------------------------------------------------
;; Util
;;------------------------------------------------------------------------------

(defn- by-id [id]
  (.getElementById js/document id))

;;------------------------------------------------------------------------------
;; App State
;;------------------------------------------------------------------------------

(def state (atom {
  :projects {
    "~/project1/project.clj" {
      :name "project1"
      :builds {
        :main {
          :errors [
            "WARNING: Use of undeclared Var cljs-gui.core/foo at line 80 src-cljs/cljs_gui/core.cljs"
            "WARNING: Use of undeclared Var cljs-gui.core/bar at line 80 src-cljs/cljs_gui/core.cljs"
          ]
          :warnings nil
          :status :done

          :cljsbuild {
            :source-paths ["src-cljs"]
            :compiler {
              :optimizations :whitespace
              :output-to "public/js/main.js"}}}

        :main-min {
          :errors nil
          :warnings nil
          :status :compiling
          :source-paths ["src-cljs"]
          :compiler {
            :optimizations :advanced
            :output-to "public/js/main.min.js"}}}}

    "~/project2/project.clj" {
      :name "project2"
      :builds {
        :main {
          :errors nil
          :warnings nil
          :status :done
          :source-paths ["src-cljs"]
          :compiler {
            :optimizations :whitespace
            :output-to "public/js/main.js"}}

        :main-min {
          :errors nil
          :warnings nil
          :status :done
          :source-paths ["src-cljs"]
          :compiler {
            :optimizations :advanced
            :output-to "public/js/main.min.js"}}}}
  }
  }))

;;------------------------------------------------------------------------------
;; Events
;;------------------------------------------------------------------------------

(defn- click-auto-btn []

  )

(defn- click-once-btn []

  )

(defn- click-clean-btn []

  )

;;------------------------------------------------------------------------------
;; Sablono Templates
;;------------------------------------------------------------------------------

(sablono/defhtml error-row [err]
  [:div.error-56f38
    [:i.fa.fa-warning] err])

(sablono/defhtml project-build [[k b]]
  [:div.build-fe180
    [:code (-> b :compiler :output-to)]
    (case (:status b)
      :compiling
        [:div.compiling-9cc92 [:i.fa.fa-gear.fa-spin] "Compiling..."]
      :done
        [:div.success-5c065 [:i.fa.fa-check] "Done"]
      )
    (if (:errors b)
      (map error-row (:errors b)))])

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

(quiescent/defcomponent ProjectRow [[k p]]
  (sablono/html
    [:div.project-row-1b83a
      [:div.project-name-ba9e7 (:name p)]
      [:div.builds-2ec4d
        (map project-build (:builds p))]]))

(quiescent/defcomponent AppRoot [app-state]
  (sablono/html
    [:div.app-ca3cd
      [:h1 "ClojureScript Compiler"]
      (map ProjectRow (:projects app-state))]))

;;------------------------------------------------------------------------------
;; State Change and Rendering
;;------------------------------------------------------------------------------

(defn- on-change-state [_kwd _the-atom _old-state new-state]
  (let [raf (aget js/window "requestAnimationFrame")
        render-fn (fn []
                    (quiescent/render (AppRoot new-state) js/document.body))]
    (raf render-fn)))

(add-watch state :main on-change-state)

;;------------------------------------------------------------------------------
;; Init
;;------------------------------------------------------------------------------

(defn init! []

  ;; trigger the initial render
  (swap! state identity))

(init!)