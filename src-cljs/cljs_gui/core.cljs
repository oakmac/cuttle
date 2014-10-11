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

(defn- num-builds [prj]
  (-> prj
    :builds
    keys
    count))

(defn- checked-builds
  "Returns a set of the keys of builds that have :checked? = true"
  [prj]
  (reduce
    (fn [checked-keys [bld-key bld]]
      (if (:checked? bld)
        (conj checked-keys bld-key)
        checked-keys))
    #{}
    (:builds prj)))

(defn- selected-builds-count [prj]
  (count (checked-builds prj)))

;;------------------------------------------------------------------------------
;; App State
;;------------------------------------------------------------------------------

(def state (atom {
  :projects {
    "~/t3tr0s/project.clj" {
      :name "t3tr0s"
      :state :idle
      :builds {
        :client {
          :checked? true
          :compile-time 0.6
          :last-compile-time 1413048033
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
          :checked? true
          :compile-time 4.7
          :last-compile-time nil
          :errors nil
          :status :done
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
          :checked? false
          :compile-time nil
          :last-compile-time nil
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
      :state :auto
      :builds {
        :main {
          :checked? true
          :errors nil
          :last-compile-time 1413048033
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
          :checked? true
          ;; will there ever be more than one error?
          :errors ["EOF while reading, starting at line 7"]
          :last-compile-time nil
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
;; State-effecting
;;------------------------------------------------------------------------------

;; NOTE: I'm sure these functions could be cleaned up / combined

(defn- mark-checked [builds build-key]
  (assoc-in builds [build-key :checked?] true))

(defn- select-all-builds! [project-key]
  (swap! state update-in [:projects project-key :builds] (fn [builds]
    (reduce mark-checked builds (keys builds)))))

(defn- mark-unchecked [builds build-key]
  (assoc-in builds [build-key :checked?] false))

(defn- unselect-all-builds! [project-key]
  (swap! state update-in [:projects project-key :builds] (fn [builds]
    (reduce mark-unchecked builds (keys builds)))))

(defn- mark-compiling [builds build-key]
  (assoc-in builds [build-key :status] :compiling))

;;------------------------------------------------------------------------------
;; Events
;;------------------------------------------------------------------------------

(defn- click-auto-btn [project-key]
  (swap! state assoc-in [:projects project-key :state] :auto))

(defn- click-stop-auto-btn [project-key]
  (swap! state assoc-in [:projects project-key :state] :idle))

(defn- demo-done-1 []
  (swap! state assoc-in [:projects "~/t3tr0s/project.clj" :builds :client :status] :done)
  )

(defn- demo-done-2 []
  (swap! state assoc-in [:projects "~/t3tr0s/project.clj" :builds :client-adv :status] :done)
  (click-stop-auto-btn "~/t3tr0s/project.clj")
  )

(defn- click-once-btn [project-key]
  (let [prj1 (get-in @state [:projects project-key])
        checked-builds-keys (checked-builds prj1)
        prj2 (assoc prj1 :state :build-once)
        new-builds (reduce mark-compiling (:builds prj2) checked-builds-keys)
        prj3 (assoc prj2 :builds new-builds)]
    (swap! state assoc-in [:projects project-key] prj3)

    ;; DEMO CODE
    (if (= project-key "~/t3tr0s/project.clj")
      (do
        (js/setTimeout demo-done-1 600)
        (js/setTimeout demo-done-2 4700))
      (js/setTimeout #(click-stop-auto-btn project-key) 1500))))

(defn- click-clean-btn [project-key]
  (swap! state assoc-in [:projects project-key :state] :clean)

  ;; DEMO CODE
  (js/setTimeout #(click-stop-auto-btn project-key) 1500))

(defn- click-checkbox-header [project-key]
  (let [p (get-in @state [:projects project-key])
        num-builds (num-builds p)
        num-selected-builds (selected-builds-count p)]
    (if (zero? num-selected-builds)
      (select-all-builds! project-key)
      (unselect-all-builds! project-key))))

(defn- click-build-row [project-key build-key]
  (swap! state update-in [:projects project-key :builds build-key :checked?] not))

;;------------------------------------------------------------------------------
;; Sablono Templates
;;------------------------------------------------------------------------------

;; TODO: ts should be a timestamp, use moment.js to produce a relative time
;; string from the last compile time, ie:
;; "a few seconds ago", "an hour ago", "2 days ago", etc
(sablono/defhtml last-compile-cell [ts]
  (if ts
    "a few seconds ago"
    "-"))

(defn- warnings-status [n]
  (str
    "Done with " n " warning"
    (if (> n 1) "s")))

(sablono/defhtml checked-cell [prj bld]
  (if (= :idle (:state prj))
    (if (:checked? bld)
      [:i.fa.fa-check-square-o]
      [:i.fa.fa-square-o])
    (if (:checked? bld)
      [:i.fa.fa-check.small-check-7b3d7]
      "-")))

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

(defn- build-row-class [idx checked?]
  (str "build-row-fdd97"
    (if (zero? (mod idx 2))
      " odd-c27e6" " even-158a9")
    (if-not checked?
      " not-selected-a8d35")))

(sablono/defhtml build-row [idx [build-key bld] project-key prj]
  [:tr {:class (build-row-class idx (:checked? bld))
        :on-click (if (= :idle (:state prj))
                    #(click-build-row project-key build-key))}
    [:td.cell-9ad24 (checked-cell prj bld)]
    [:td.cell-9ad24 (-> bld :cljsbuild :source-paths first)] ;; TODO: print the vector here
    [:td.cell-9ad24 (-> bld :cljsbuild :compiler :output-to)]
    [:td.cell-9ad24 (status-cell bld)]
    [:td.cell-9ad24 (last-compile-cell (:last-compile-time bld))]
    [:td.cell-9ad24 (-> bld :cljsbuild :compiler :optimizations name)]]
  (when (:warnings bld)
    (map warning-row (:warnings bld)))
  (when (:errors bld)
    (map error-row (:errors bld))))

;; NOTE: these two functions could be combined

(sablono/defhtml start-auto-btn [project-key num-builds]
  (if (zero? num-builds)
    [:button.disabled-btn-1884b
      {:disabled true}
      "Start Auto" [:span.count-cfa27 (str "[" num-builds "]")]]
    [:button.btn-da85d
      {:on-click #(click-auto-btn project-key)}
      "Start Auto" [:span.count-cfa27 (str "[" num-builds "]")]]))

(sablono/defhtml build-once-btn [project-key num-builds]
  (if (zero? num-builds)
    [:button.disabled-btn-1884b
      {:disabled true}
      "Build Once" [:span.count-cfa27 (str "[" num-builds "]")]]
    [:button.btn-da85d
      {:on-click #(click-once-btn project-key)}
      "Build Once" [:span.count-cfa27 (str "[" num-builds "]")]]))

(sablono/defhtml checkbox-header [num-selected-builds num-builds]
  (cond
    (= num-selected-builds num-builds) [:i.fa.fa-check-square-o]
    (zero? num-selected-builds) [:i.fa.fa-square-o]
    :else [:i.fa.fa-minus-square-o]))

(sablono/defhtml idle-buttons [project-key num-selected-builds]
  (start-auto-btn project-key num-selected-builds)
  (build-once-btn project-key num-selected-builds)
  [:button.btn-da85d
    {:on-click #(click-clean-btn project-key)}
    "Clean"])

(sablono/defhtml auto-buttons [project-key]
  [:button.btn-da85d
    {:on-click #(click-stop-auto-btn project-key)}
    "Stop Auto"])

(sablono/defhtml project-status [st]
  (cond
    (= :auto st) [:span.status-b8614 "auto compiling..."]
    (= :build-once st) [:span.status-b8614 "building once..."]
    (= :clean st) [:span.status-b8614 "cleaning..."]
    :else [:span.edit-c0ba4 "edit"]))

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

;; TODO: should probably make the TableHeader and BuildRow components

(quiescent/defcomponent Project [[project-key prj]]
  (let [num-builds (num-builds prj)
        num-selected-builds (selected-builds-count prj)]
    (sablono/html
      [:div.project-1b83a
        [:div.wrapper-714e4
          [:div.project-name-ba9e7
            (:name prj)
            (project-status (:state prj))]
          [:div.project-btns-f5656
            (cond
              (= :idle (:state prj))
                (idle-buttons project-key num-selected-builds)
              (= :auto (:state prj))
                (auto-buttons project-key))]
          [:div.clr-737fa]]
        [:table.tbl-bdf39
          [:thead
            [:tr.header-row-50e32
              (if (= :idle (:state prj))
                [:th.th-92ca4
                  {:on-click #(click-checkbox-header project-key)}
                  (checkbox-header num-selected-builds num-builds) "Compile?"]
                [:th.th-92ca4
                  [:i.fa.fa-check.small-check-7b3d7] "Compile?"])
              [:th.th-92ca4 "Source"]
              [:th.th-92ca4 "Output"]
              [:th.th-92ca4 {:style {:width "30%"}} "Status"]
              [:th.th-92ca4 "Last Compile"]
              [:th.th-92ca4 "Optimizations"]]]
          [:tbody
            (map-indexed #(build-row %1 %2 project-key prj) (:builds prj))]]])))

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