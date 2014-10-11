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
          :compile-time 0.6
          :errors nil
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
          :compile-time 4.7
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
              :pretty-print false }}}

        :server {
          :compile-time 2.2
          :errors nil
          :status :missing
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
          :status :done-with-warnings
          :warnings [
            "Use of undeclared Var project2.core/foo at line 79 src-cljs/project2/core.cljs"
            "Use of undeclared Var project2.core/bar at line 80 src-cljs/project2/core.cljs"
          ]

          :cljsbuild {
            :source-paths ["src-cljs"]
            :compiler {
              :optimizations :whitespace
              :output-to "public/js/main.js"}}}

        :main-min {
          ;; will there ever be more than one error?
          :errors ["EOF while reading, starting at line 7"]
          :status :errors
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

(defn- warnings-status [n]
  (str
    "Done with " n " warning"
    (if (> n 1) "s")))

(sablono/defhtml status-cell [{:keys [compile-time status warnings]}]
  (case status
    :cleaning
      [:span.cleaning-a1438 [:i.fa.fa-gear.fa-spin] "Cleaning..."]
    :compiling
      [:span.compiling-9cc92 [:i.fa.fa-gear.fa-spin] "Compiling..."]
    :done
      [:span.success-5c065
        [:i.fa.fa-check] (str "Done in " compile-time " seconds")]
    :done-with-warnings
      [:span.with-warnings-4b105
        [:i.fa.fa-exclamation-triangle] (warnings-status (count warnings))]
    :errors
      [:span.errors-2718a [:i.fa.fa-times] "Compiling failed"]
    :missing
      [:span.missing-f02af [:i.fa.fa-minus-circle] "Output missing"]
    "*unknkown status*"))

(defn- row-color [idx]
  (if (zero? (mod idx 2))
    "#fafafa" "#fff"))

(sablono/defhtml error-row [err]
  [:tr.error-row-b3028
    [:td.error-cell-1ccea {:col-span "6"}
      [:i.fa.fa-times] err]])

(sablono/defhtml warning-row [w]
  [:tr.warning-row-097c8
    [:td.warning-cell-b9f12 {:col-span "6"}
      [:i.fa.fa-exclamation-triangle] w]])

(sablono/defhtml build-row [idx [k b]]
  [:tr.build-row-fdd97 {:style {:background-color (row-color idx)}}
    [:td.cell-9ad24 [:i.fa.fa-square-o]]
    [:td.cell-9ad24 (-> b :cljsbuild :source-paths first)] ;; TODO: print the vector here
    [:td.cell-9ad24 (-> b :cljsbuild :compiler :output-to)]
    [:td.cell-9ad24 (status-cell  b)]
    [:td.cell-9ad24 "a few seconds ago"]
    [:td.cell-9ad24 (-> b :cljsbuild :compiler :optimizations name)]]
  (if (:warnings b)
    (map warning-row (:warnings b)))
  (if (:errors b)
    (map error-row (:errors b))))

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

(quiescent/defcomponent Project [[k p]]
  (sablono/html
    [:div.project-1b83a
      [:div.wrapper-714e4
        [:div.project-name-ba9e7
          (:name p)
          [:span.edit-c0ba4 "edit"]]
        [:div.project-btns-f5656
          [:button.btn-da85d "Start Auto"]
          [:button.btn-da85d "Build Once"]
          [:button.btn-da85d "Clean"]]
        [:div.clr-737fa]]
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