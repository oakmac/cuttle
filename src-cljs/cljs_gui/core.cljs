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
    "~/t3tr0s/project.clj" {
      :name "t3tr0s"
      :builds {
        :client {
          :errors [
            "WARNING: Use of undeclared Var cljs-gui.core/foo at line 80 src-cljs/cljs_gui/core.cljs"
            "WARNING: Use of undeclared Var cljs-gui.core/bar at line 80 src-cljs/cljs_gui/core.cljs"
          ]
          :status :done
          :warnings nil

          ;; copied directly from project.clj
          :cljsbuild {
            :source-paths ["src/client"]
            :compiler {
              :output-to "public/js/client.js"
              :output-dir "public/out"
              :optimizations :whitespace }}}

        :client-adv {
          :errors nil
          :status :compiling
          :warnings nil

          ;; copied directly from project.clj
          :cljsbuild {
            :source-paths ["src/client"]
            :compiler {
              :externs ["externs/jquery-1.9.js" "externs/socket.io.js"]
              :output-to "public/js/client.min.js"
              :optimizations :advanced
              :pretty-print false}}}

        :server {
          :errors nil
          :status :done
          :warnings nil

          ;; copied directly from project.clj
          :cljsbuild {
            :source-paths ["src/server"]
            :compiler {
              :language-in :ecmascript5
              :language-out :ecmascript5
              :target :nodejs
              :output-to "server.js"
              :optimizations :simple }}}}}

    "~/project2/project.clj" {
      :name "project2"
      :builds {
        :main {
          :errors nil
          :status :done
          :warnings nil

          :cljsbuild {
            :source-paths ["src-cljs"]
            :compiler {
              :optimizations :whitespace
              :output-to "public/js/main.js"}}}

        :main-min {
          :errors nil
          :status :done
          :warnings nil

          :cljsbuild {
            :source-paths ["src-cljs"]
            :compiler {
              :optimizations :advanced
              :output-to "public/js/main.min.js"}}}}}
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

(sablono/defhtml status-cell [st]
  (case st
    :compiling
      [:span.compiling-9cc92 [:i.fa.fa-gear.fa-spin] "Compiling..."]
    :done
      [:span.success-5c065 [:i.fa.fa-check] "Done in 0.6 seconds"]
    "*unknkown status*"))

(defn- row-color [idx]
  (if (zero? (mod idx 2))
    "#fafafa" "#fff"))

(sablono/defhtml build-row [idx [k b]]
  [:tr.build-row-fdd97 {:style {:background-color (row-color idx)}}
    [:td.cell-9ad24 [:i.fa.fa-square-o]]
    [:td.cell-9ad24 (-> b :cljsbuild :source-paths first)] ;; TODO: print the vector here
    [:td.cell-9ad24 (-> b :cljsbuild :compiler :output-to)]
    [:td.cell-9ad24 (status-cell (:status b))]
    [:td.cell-9ad24 "a few seconds ago"]
    [:td.cell-9ad24 (-> b :cljsbuild :compiler :optimizations name)]])

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

(quiescent/defcomponent Project [[k p]]
  (sablono/html
    [:div.project-row-1b83a
      [:div.project-name-ba9e7
        (:name p)
        [:span.edit-c0ba4 "edit"]]
      [:table.tbl-bdf39
        [:thead
          [:tr.header-row-50e32
            [:th.th-92ca4 "Compile"]
            [:th.th-92ca4 "Source"]
            [:th.th-92ca4 "Output"]
            [:th.th-92ca4 "Status"]
            [:th.th-92ca4 "Last Compile"]
            [:th.th-92ca4 "Optimizations"]]]
        [:tbody
          (map-indexed build-row (:builds p))]]]))

(quiescent/defcomponent AppRoot [app-state]
  (sablono/html
    [:div.app-ca3cd
      [:div.header-a4c14
        [:div.title-8749a "ClojureScript Compiler"]
        ;;[:div.settings-link-c1709 [:i.fa.fa-gear]]
        [:div.title-links-42b06
          [:span.link-3d3ad [:i.fa.fa-plus] "Add project"]
          [:span.link-3d3ad [:i.fa.fa-gear] "Settings"]]
        [:div.clr-737fa]]
      (map Project (:projects app-state))]))

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