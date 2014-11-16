(ns cljsbuild-ui.pages.main
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [quiescent :include-macros true]
    [sablono.core :as sablono :include-macros true]
    [cljsbuild-ui.config :refer [config]]
    [cljsbuild-ui.exec :as exec]
    [cljsbuild-ui.util :refer [date-format log js-log now uuid]]))

;;------------------------------------------------------------------------------
;; App State
;;------------------------------------------------------------------------------

(def initial-project-build-state
  "Initial state for a new build in a project"
  {;; tool state
   :active? true
   :compile-time 0
   :last-compile-time nil
   :error nil
   :state :blank
   :warnings []

   ;; from project.clj
   :id nil
   :source-paths []
   :compiler {}})

(def initial-project-state
  "Initial state for a new project"
  {;; tool state
   :compile-menu-showing? false
   :auto-compile? true
   :state :idle

   ;; from project.clj
   :name ""
   :filename ""
   :version ""
   :license {}
   :dependencies []
   :plugins []

   ;; from project.clj, but with extra tool state
   ;; (see initial-project-build-state)
   :builds []})

(def initial-app-state
  {:projects {:order []}})

(def state (atom initial-app-state))

(defn get-ordered-projects
  "Given a project map containing a list of keys in :order, return a vector of their ordered values."
  [project-map]
  (let [proj-keys (-> project-map :order)]
    (mapv #(get project-map %) proj-keys)))

(def test-app-state
  "A test app-state for quick-testing page rendering."
  {
  :projects {
    "/home/oakmac/t3tr0s/project.clj" {
      :compile-menu-showing? false
      :auto-compile? true
      :name "t3tr0s"
      :state :idle
      :builds [
        { :id :client
          :active? true
          :compile-time 0.6
          :last-compile-time nil
          :error nil
          :state :done
          :warnings []

          ;; copied directly from project.clj
          :source-paths ["src/client"]
          :compiler {
            :output-to "public/js/client.js"
            :output-dir "public/out"
            :optimizations :whitespace }}

        { :id :client-adv
          :active? true
          :compile-time 4.7
          :last-compile-time nil
          :error nil
          :state :done
          :warnings []

          ;; copied directly from project.clj
          :source-paths ["src/client"]
          :compiler {
            :externs ["externs/jquery-1.9.js" "externs/socket.io.js"]
            :output-to "public/js/client.min.js"
            :optimizations :advanced
            :pretty-print false }}

        { :id :server
          :active? true
          :compile-time nil
          :last-compile-time nil
          :error nil
          :state :missing
          :warnings []

          ;; copied directly from project.clj
          :source-paths ["src/server"]
          :compiler {
            :language-in :ecmascript5
            :language-out :ecmascript5
            :target :nodejs
            :output-to "server.js"
            :optimizations :simple }}]}

    "/home/oakmac/project2/project.clj" {
      :auto-compile? false
      :compile-menu-showing? false
      :name "project2"
      :state :auto
      :builds [
        { :id :main
          :active? true
          :error nil
          :last-compile-time 1413048033
          :state :done-with-warnings
          :warnings [
            "Use of undeclared Var project2.core/foo at line 79 src-cljs/project2/core.cljs"
            "Use of undeclared Var project2.core/bar at line 80 src-cljs/project2/core.cljs"
          ]

          :source-paths ["src-cljs"]
          :compiler {
            :optimizations :whitespace
            :output-to "public/js/main.js"}}

        { :id :main-min
          :active? true
          :error ["EOF while reading, starting at line 7"]
          :last-compile-time nil
          :state :done-with-error
          :warnings []

          :source-paths ["src-cljs"]
          :compiler {
            :optimizations :advanced
            :output-to "public/js/main.min.js"}}]}}})

;;------------------------------------------------------------------------------
;; Util
;;------------------------------------------------------------------------------

(defn- by-id [id]
  (.getElementById js/document id))

;; NOTE: this function will not work if two builds have the same output-to target
;; should probably refactor with a filter?
(defn- output-to->bld-id [prj-key output-to]
  (let [blds (get-in @state [:projects prj-key :builds])
        outputs (map #(-> % :compiler :output-to) blds)
        ids (map :id blds)
        m (zipmap outputs ids)]
    (get m output-to)))

;; NOTE: this could probably be written cleaner with a threading macro
(defn- bld-id->idx [prj-key bld-id]
  (first
    (remove nil?
      (map-indexed
        (fn [idx bld]
          (if (= bld-id (:id bld)) idx))
        (get-in @state [:projects prj-key :builds])))))

(defn- num-selected-builds [prj]
  (->> prj
    :builds
    (map :active?)
    (remove false?)
    count))

(defn- get-active-bld-ids [prj]
  (->> prj
    :builds
    (filter #(true? (:active? %)))
    (map :id)))

;;------------------------------------------------------------------------------
;; State-effecting
;;------------------------------------------------------------------------------

;; NOTE: I'm sure these functions could be cleaned up / combined

;; NOTE: I'm fairly certain this could be written cleaner with a reduce
(defn- update-now!
  "Update the :now timestamp on every build so we can see relative time since
   the last compile."
  []
  (let [n (now)
        prj-keys (-> @state :projects :order)]
    (doall
      (map
        (fn [prj-key]
          (let [bld-ids (keys (:builds (get-in @state [:projects prj-key])))]
            (doall
              (map
                (fn [bld-id]
                  (swap! state assoc-in [:projects prj-key :builds bld-id :now] n))
                bld-ids))))
        prj-keys))))

;; TODO: commenting this out until we figure out what to do with the last
;; compile time display
;; update the now timestamps every 0.25 second
;; (js/setInterval update-now! 250)

(defn- clear-compiled-info! [prj-key bld-idx]
  (swap! state update-in [:projects prj-key :builds bld-idx]
    assoc :last-compile-time nil
          :error nil
          :warnings []))

(defn- remove-compiled-info!
  "Removes compiled info from a build: last-compile-time, error, warnings"
  [prj-key]
  (doall
    (map-indexed
      #(clear-compiled-info! prj-key %1)
      (get-in @state [:projects prj-key :builds]))))

(defn- show-waiting! [prj-key]
  (doall
    (map-indexed
      #(swap! state assoc-in [:projects prj-key :builds %1 :state] :waiting)
      (get-in @state [:projects prj-key :builds]))))

(defn- show-start-compiling! [prj-key bld-id]
  (let [bld-idx (bld-id->idx prj-key bld-id)]
    (clear-compiled-info! prj-key bld-idx)
    (swap! state assoc-in [:projects prj-key :builds bld-idx :state] :compiling)))

(defn- show-done-compiling! [prj-key bld-id compile-time]
  (let [bld-idx (bld-id->idx prj-key bld-id)
        map-path [:projects prj-key :builds bld-idx]
        bld (get-in @state map-path)
        new-state (if (-> bld :warnings empty?) :done :done-with-warnings)
        new-bld (assoc bld :compile-time compile-time
                           :last-compile-time (now)
                           :state new-state)]
    (swap! state assoc-in map-path new-bld)))

(defn- show-warnings! [prj-key bld-id warnings]
  (let [bld-idx (bld-id->idx prj-key bld-id)]
    (swap! state update-in [:projects prj-key :builds bld-idx :warnings] (fn [w]
      (into [] (concat w warnings))))))

(defn- show-build-error! [prj-key bld-id error-msg]
  (let [bld-idx (bld-id->idx prj-key bld-id)]
    (swap! state update-in [:projects prj-key :builds bld-idx]
      assoc :error error-msg :state :done-with-error)))

(defn- show-finished!
  "Mark a project as being finished with compiling. ie: idle state"
  [prj-key]
  (swap! state assoc-in [:projects prj-key :state] :idle)

  ;; TODO: update builds here
  )

(defn- show-lein-startup! [prj-key bld-id]
  (let [bld-idx (bld-id->idx prj-key bld-id)]
    (swap! state assoc-in [:projects prj-key :builds bld-idx :state] :lein-startup)))

;;------------------------------------------------------------------------------
;; Compiler Interface
;;------------------------------------------------------------------------------

;; NOTE: this could be simplified with a go-loop?
;; TODO: "current-bld-id" should probably be in exec.cljs and passed through
;; the channel
(defn- handle-compiler-output
  "This function reads from the console output channel and updates the UI.
   NOTE: recursive function, terminating case is when the channel is closed"
  [c prj-key current-bld-id first-output?]
  (go
    (when-let [[type data] (<! c)]

      (when (:log-compiler-output config)
        (js-log "Channel contents:")
        (log type)
        (log data)
        (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"))

      (when @first-output?
        (show-waiting! prj-key)
        (reset! first-output? false))

      (case type
        :start
          (let [bld-id (output-to->bld-id prj-key data)]
            (reset! current-bld-id bld-id)
            (show-start-compiling! prj-key bld-id))
        :success
          (do
            (show-done-compiling! prj-key @current-bld-id data)
            (reset! current-bld-id nil))
        :warning
          (show-warnings! prj-key @current-bld-id data)
        :finished
          (show-finished! prj-key)
        :error
          (show-build-error! prj-key @current-bld-id data)
        nil)

      ;; loop back
      (handle-compiler-output c prj-key current-bld-id first-output?))))

(defn- start-auto-compile! [prj-key bld-ids]
  ;; show project loading state
  (swap! state assoc-in [:projects prj-key :state] :auto)

  ;; update the BuildRows state
  (doall (map #(show-lein-startup! prj-key %) bld-ids))

  (remove-compiled-info! prj-key)

  ;; start the build
  (let [compiler-chan (exec/start-auto prj-key bld-ids)]
    (handle-compiler-output compiler-chan prj-key (atom nil) (atom true))))

(defn- start-compile-once! [prj-key bld-ids]
  ;; show project state
  (swap! state assoc-in [:projects prj-key :state] :once)

  ;; update the BuildRows state
  (doall (map #(show-lein-startup! prj-key %) bld-ids))

  (remove-compiled-info! prj-key)

  ;; start the build
  (let [compiler-chan (exec/build-once prj-key bld-ids)]
    (handle-compiler-output compiler-chan prj-key (atom nil) (atom true))))

;; TODO: deal with clean errors
(defn- on-clean-error [stderr]
  (js-log "TODO: handle clean error"))

(defn- on-clean-success [prj-key]
  ;; set project state
  (swap! state assoc-in [:projects prj-key :state] :idle)

  ;; set builds state
  (doall
    (map-indexed
      (fn [idx bld]
        (swap! state update-in [:projects prj-key :builds idx]
          assoc :state :missing))
      (get-in @state [:projects prj-key :builds]))))

;;------------------------------------------------------------------------------
;; Events
;;------------------------------------------------------------------------------

;; TODO: this will not work if they click outside the app root element
;; need to refactor
(defn- click-root []
  (doall
    (map
      (fn [prj-key]
        (swap! state assoc-in [:projects prj-key :compile-menu-showing?] false))
      (-> @state :projects :order))))

(defn- click-compile-btn [prj-key]
  (let [prj (get-in @state [:projects prj-key])
        bld-ids (get-active-bld-ids prj)]
    ;; safeguard
    (when-not (zero? (count bld-ids))
      (if (:auto-compile? prj)
        (start-auto-compile! prj-key bld-ids)
        (start-compile-once! prj-key bld-ids)))))

(defn- click-stop-auto-btn [prj-key]
  ;; update project state
  (swap! state assoc-in [:projects prj-key :state] :idle)

  ;; stop the process
  (exec/stop-auto prj-key))

(defn- click-clean-btn [prj-key]
  ;; update project state
  (swap! state assoc-in [:projects prj-key :state] :cleaning)

  ;; mark the build rows
  (doall
    (map-indexed
      #(swap! state update-in [:projects prj-key :builds %1]
          assoc :active? true :state :cleaning)
      (get-in @state [:projects prj-key :builds])))

  ;; remove any errors / warnings from BuildRows
  (remove-compiled-info! prj-key)

  ;; start the clean
  (exec/clean prj-key #(on-clean-success prj-key) on-clean-error))

(defn- click-build-row [prj-key build-key]
  (swap! state update-in [:projects prj-key :builds build-key :checked?] not))

(defn- click-compile-options [js-evt prj-key]
  (.stopPropagation js-evt)
  (swap! state update-in [:projects prj-key :compile-menu-showing?] not))

(defn- toggle-auto-compile [prj-key]
  (swap! state update-in [:projects prj-key :auto-compile?] not))

(defn- toggle-build-active [prj-key bld-idx]
  (swap! state update-in [:projects prj-key :builds bld-idx :active?] not))

;;------------------------------------------------------------------------------
;; Sablono Templates
;;------------------------------------------------------------------------------

(sablono/defhtml bld-tbl-hdr []
  [:thead
    [:tr.header-row-50e32
      [:th.th-92ca4 "Build ID"]
      [:th.th-92ca4 "Source"]
      [:th.th-92ca4 "Output"]
      [:th.th-92ca4 {:style {:width "30%"}} "Status"]
      [:th.th-92ca4 "Last Compile"]
      [:th.th-92ca4 "Optimizations"]]])

;; TODO: this is unfinished; I think we should toggle between a date/time
;; display of the last compile and a relative "time ago" display, maybe just by
;; clicking on the field?
(sablono/defhtml last-compile-cell [bld]
  (let [n (:now bld)
        compile-time (:last-compile-time bld)
        seconds-diff (- n compile-time)
        minutes-diff (js/Math.floor (/ seconds-diff 60))
        seconds-diff2 (- seconds-diff (* 60 minutes-diff))]
    (if-not (:last-compile-time bld)
      "-"
      [:span.time-fc085 (date-format (:last-compile-time bld) "HH:mm:ss")]
      ;; NOTE: the idea below is too distracting; need to come up with a better
      ;; way
      ; [:span.time-fc085
      ;   (str (if-not (zero? minutes-diff)
      ;          (str minutes-diff "m "))
      ;        seconds-diff2 "s ago")]
        )))

(defn- warnings-state [n]
  (str
    "Done with " n " warning"
    (if (> n 1) "s")))

(sablono/defhtml state-cell [{:keys [compile-time state warnings]}]
  (case state
    :blank
      [:span "-"]
    :cleaning
      [:span [:i.fa.fa-gear.fa-spin] "Cleaning..."]
    :compiling
      [:span.compiling-9cc92 [:i.fa.fa-gear.fa-spin] "Compiling..."]
    :done
      [:span.success-5c065
        [:i.fa.fa-check] (str "Done in " compile-time " seconds")]
    :done-with-warnings
      [:span.with-warnings-4b105
        [:i.fa.fa-exclamation-triangle] (warnings-state (count warnings))]
    :done-with-error
      [:span.errors-2718a [:i.fa.fa-times] "Compiling failed"]
    :lein-startup
      [:span [:i.fa.fa-gear.fa-spin] "Leiningen starting..."]
    :missing
      [:span [:i.fa.fa-minus-circle] "Output missing"]
    :waiting
      [:span [:i.fa.fa-clock-o] "Waiting..."]
    "*unknown state*"))

(sablono/defhtml error-row [error-msg]
  [:tr.error-row-b3028
    [:td.error-cell-1ccea {:col-span "6"}
      [:i.fa.fa-times]
      (if (coll? error-msg)
        (map (fn [l] (sablono/html [:div l])) error-msg)
        error-msg)]])

(sablono/defhtml warning-row [w]
  [:tr.warning-row-097c8
    [:td.warning-cell-b9f12 {:col-span "6"}
      [:i.fa.fa-exclamation-triangle] w]])

(defn- build-row-class [{:keys [idx active?]}]
  (str "build-row-fdd97 "
    (if (zero? (mod idx 2))
      "odd-c27e6"
      "even-158a9")
    (if-not active?
      " not-active-a8d35")))

(sablono/defhtml build-option [idx bld prj-key]
  [:div.bld-e7c4d
    {:on-click #(toggle-build-active prj-key idx)}
    (if (:active? bld)
      [:i.fa.fa-check-square-o]
      [:i.fa.fa-square-o])
    (-> bld :id name)])

(sablono/defhtml compile-menu [prj-key prj]
  [:div.menu-b4b27
    {:on-click #(.stopPropagation %)}
    [:label.small-cffc5 "options"]
    [:label.label-ec878
      {:on-click #(toggle-auto-compile prj-key)}
      (if (:auto-compile? prj)
        [:i.fa.fa-check-square-o]
        [:i.fa.fa-square-o])
      "Auto Compile"]
    [:div.spacer-685b6]
    [:label.small-cffc5 "builds"]
    (map-indexed #(build-option %1 %2 prj-key) (:builds prj))])

(sablono/defhtml idle-state [prj-key prj]
  [:button.compile-btn-17a78
    {:on-click #(click-compile-btn prj-key)}
    (if (:auto-compile? prj)
      "Auto Compile"
      "Compile Once")
    [:span.count-cfa27 (str "[" (num-selected-builds prj) "]")]]
  [:button.menu-btn-550bf
    {:on-click #(click-compile-options % prj-key)}
    [:i.fa.fa-caret-down]]
  [:button.btn-da85d
    {:on-click #(click-clean-btn prj-key)}
    "Clean"]
  (when (:compile-menu-showing? prj)
    (compile-menu prj-key prj)))

(sablono/defhtml auto-state [prj-key]
  [:span.status-984ee "Auto compiling ClojureScript..."]
  [:button.btn-da85d
    {:on-click #(click-stop-auto-btn prj-key)}
    "Stop"])

(sablono/defhtml cleaning-state [prj-key]
  [:span.status-984ee "Removing generated files..."])

(sablono/defhtml once-state [prj-key]
  [:span.status-984ee "Compiling ClojureScript..."])

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

(quiescent/defcomponent BuildRow [bld]
  (sablono/html
    [:tbody
      [:tr {:class (build-row-class bld)}
        [:td.cell-9ad24 (-> bld :id name)]
        [:td.cell-9ad24 (-> bld :source-paths first)] ;; TODO: need to print the vector here
        [:td.cell-9ad24 (-> bld :compiler :output-to)]
        [:td.cell-9ad24 (state-cell bld)]
        [:td.cell-9ad24 (last-compile-cell bld)]
        [:td.cell-9ad24 (-> bld :compiler :optimizations name)]]
      (when (:error bld)
        (error-row (:error bld)))
      (when (and (:warnings bld)
                 (not (zero? (:warnings bld))))
        (map warning-row (:warnings bld)))]))

(quiescent/defcomponent Project [prj]
  (let [prj-key (:filename prj)]
    (sablono/html
      [:div.project-1b83a
       [:div.wrapper-714e4
        [:div.left-ba9e7
         (:name prj)
         (when (= (:state prj) :idle)
           [:span.edit-c0ba4 "edit"])]
        [:div.right-f5656
         (case (:state prj)
           :auto (auto-state prj-key)
           :cleaning (cleaning-state prj-key)
           :idle (idle-state prj-key prj)
           :once (once-state prj-key)
           "*unknown project state*")]]
       [:table.tbl-bdf39
        (bld-tbl-hdr)
        (map-indexed
          #(BuildRow (assoc %2 :idx %1 :prj-key prj-key))
          (:builds prj))]])))

(quiescent/defcomponent AppRoot [app-state]
  (sablono/html
    [:div.app-ca3cd
      {:on-click click-root}
      [:div.header-a4c14
        [:div.title-8749a "ClojureScript Compiler"]
        [:div.title-links-42b06
          #_[:span.link-3d3ad [:i.fa.fa-plus] "Add project"]
          [:span.link-3d3ad [:i.fa.fa-gear] "Settings"]]
        [:div.clr-737fa]]
      (map Project (get-ordered-projects (:projects app-state)))]))

;;------------------------------------------------------------------------------
;; State Change and Rendering
;;------------------------------------------------------------------------------

(def request-anim-frame (aget js/window "requestAnimationFrame"))
(def cancel-anim-frame (aget js/window "cancelAnimationFrame"))
(def anim-frame-id nil)

(defn- on-change-state [_kwd _the-atom _old-state new-state]
  ;; cancel any previous render functions if they happen before the next
  ;; animation frame
  (when anim-frame-id
    (cancel-anim-frame anim-frame-id)
    (set! anim-frame-id nil))

  ;; put the render function on the next animation frame
  (let [render-fn #(quiescent/render (AppRoot new-state) (by-id "app"))]
    (set! anim-frame-id (request-anim-frame render-fn))))

(add-watch state :main on-change-state)

;;------------------------------------------------------------------------------
;; Init
;;------------------------------------------------------------------------------

(def events-added? (atom false))

(defn- add-events!
  "Add events that are outside the react.js event system.
   NOTE: this is a run-once function"
  []
  (when-not @events-added?
    ;; TODO: we may not even use this
    (reset! events-added? true)))

(defn attach-state-to-build
  "Attach state to project-build map, and prune out unused keys"
  [build]
  (let [keep-keys [:id
                   :source-paths
                   :compiler]]
    (-> initial-project-build-state
        (merge (select-keys build keep-keys)))))

(defn attach-state-to-proj
  "Attach state to project map, and prune out unused keys"
  [project]
  (let [keep-keys [:filename
                   :name
                   :version
                   :license
                   :dependencies
                   :plugins]
        builds (->> project :cljsbuild :builds (mapv attach-state-to-build))]
    (-> initial-project-state
        (merge (select-keys project keep-keys))
        (assoc :builds builds))))

(defn add-project!
  [project]
  (when-not (contains? (:projects @state) (:filename project))
    (let [filename (:filename project)
          project2 (attach-state-to-proj project)
          new-state (-> @state
                        (assoc-in [:projects filename] project2)
                        (update-in [:projects :order] conj filename))]
      (reset! state new-state))))

(defn init-projects!
  [projects]
  (let [projects2 (map attach-state-to-proj projects)
        filenames (mapv :filename projects2)
        project-map (assoc (zipmap filenames projects2)
                           :order filenames)]
    (swap! state assoc :projects project-map)))

(defn init! [projs]
  (init-projects! projs)
  (add-events!))
