(ns cljsbuild-ui.pages.main
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [quiescent :include-macros true]
    [sablono.core :as sablono :include-macros true]
    [cljsbuild-ui.exec :as exec]
    [cljsbuild-ui.util :refer [log js-log now uuid]]))

;;------------------------------------------------------------------------------
;; App State
;;------------------------------------------------------------------------------

(def state (atom {
  :projects {
    "/home/oakmac/t3tr0s/project.clj" {
      :name "t3tr0s"
      :state :idle
      :builds {
        :client {
          :checked? true
          :compile-time 0.6
          :last-compile-time nil
          :error nil
          :status :done
          :warnings []

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
          :error nil
          :status :done
          :warnings []

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
          :error nil
          :status :missing
          :warnings []

          ;; copied directly from project.clj
          :cljsbuild {
            :source-paths ["src/server"]
            :compiler {
              :language-in :ecmascript5
              :language-out :ecmascript5
              :target :nodejs
              :output-to "server.js"
              :optimizations :simple }}}}}

    "/home/oakmac/project2/project.clj" {
      :name "project2"
      :state :auto
      :builds {
        :main {
          :checked? true
          :error nil
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
          :error "EOF while reading, starting at line 7"
          :last-compile-time nil
          :status :done-with-error
          :warnings []

          :cljsbuild {
            :source-paths ["src-cljs"]
            :compiler {
              :optimizations :advanced
              :output-to "public/js/main.min.js"}}}}}
  }
  }))

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

;; NOTE: this function is definitely a little weird; makes me wonder if we have
;; the right data structure
;; There are probably bugs here.
(defn- output-to->bld-key [prj-key output-to]
  (let [blds (get-in @state [:projects prj-key :builds])
        matches (filter
                  (fn [[k v]]
                    (= output-to (-> v :cljsbuild :compiler :output-to)))
                  blds)]
    (-> matches first first)))

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
        prj-keys (keys (:projects @state))]
    (doall
      (map
        (fn [prj-key]
          (let [bld-keys (keys (:builds (get-in @state [:projects prj-key])))]
            (doall
              (map
                (fn [bld-key]
                  (swap! state assoc-in [:projects prj-key :builds bld-key :now] n))
                bld-keys))))
        prj-keys))))

;; update the now timestamps every 5 seconds
(js/setInterval update-now! 5000)

(defn- mark-checked [builds build-key]
  (assoc-in builds [build-key :checked?] true))

(defn- select-all-builds! [prj-key]
  (swap! state update-in [:projects prj-key :builds] (fn [builds]
    (reduce mark-checked builds (keys builds)))))

(defn- mark-unchecked [builds build-key]
  (assoc-in builds [build-key :checked?] false))

(defn- unselect-all-builds! [prj-key]
  (swap! state update-in [:projects prj-key :builds] (fn [builds]
    (reduce mark-unchecked builds (keys builds)))))

(defn- mark-waiting [builds build-key]
  (assoc-in builds [build-key :status] :waiting))

(defn- mark-build-for-cleaning [blds bld-key]
  (let [b (get blds bld-key)]
    (assoc blds bld-key (assoc b :checked? true
                                 :status :cleaning))))

(defn- mark-builds-for-cleaning [blds]
  (reduce mark-build-for-cleaning blds (keys blds)))

(defn- remove-errors-and-warnings! [prj-key]
  (doall
    (map
      (fn [bld-key]
        (swap! state assoc-in [:projects prj-key :builds bld-key :error] nil)
        (swap! state assoc-in [:projects prj-key :builds bld-key :warnings] []))
      (keys (get-in @state [:projects prj-key :builds])))))

(defn- show-start-compiling! [prj-key bld-key]
  (swap! state assoc-in [:projects prj-key :builds bld-key :status] :compiling))

(defn- show-done-compiling! [prj-key bld-key compile-time]
  (let [map-path [:projects prj-key :builds bld-key]
        bld (get-in @state map-path)
        new-status (if (-> bld :warnings empty?) :done :done-with-warnings)
        new-bld (assoc bld :compile-time compile-time
                           :last-compile-time (now)
                           :status new-status)]
    (swap! state assoc-in map-path new-bld)))

(defn- show-warnings! [prj-key bld-key warnings]
  (swap! state update-in [:projects prj-key :builds bld-key :warnings] (fn [w]
    (into [] (concat w warnings)))))

(defn- show-error! [prj-key bld-key err-msg]
  (swap! state assoc-in [:projects prj-key :builds bld-key :error] err-msg)
  (swap! state assoc-in [:projects prj-key :builds bld-key :status] :done-with-error))

(defn- show-finished!
  "Mark a project as being finished with compiling. ie: idle state"
  [prj-key]
  (swap! state assoc-in [:projects prj-key :state] :idle))

;;------------------------------------------------------------------------------
;; Events
;;------------------------------------------------------------------------------

(defn- click-auto-btn [prj-key]
  (remove-errors-and-warnings! prj-key)
  (swap! state assoc-in [:projects prj-key :state] :auto))

(defn- click-stop-auto-btn [prj-key]
  (swap! state assoc-in [:projects prj-key :state] :idle))

;; NOTE: this could be simplified with a go-loop?
(defn- handle-compiler-output
  "This function reads from the console output channel and updates the UI.
   NOTE: recursive function, terminating case is when the channel is closed"
  [c prj-key current-bld-key]
  (go
    (when-let [[type data] (<! c)]
      (cond
        (= type :start)
          (let [bld-key (output-to->bld-key prj-key data)]
            (reset! current-bld-key bld-key)
            (show-start-compiling! prj-key bld-key))
        (= type :success)
          (do (show-done-compiling! prj-key @current-bld-key data)
              (reset! current-bld-key nil))
        (= type :warning)
          (show-warnings! prj-key @current-bld-key data)
        (= type :finished)
          (show-finished! prj-key)
        (= type :error)
          (show-error! prj-key @current-bld-key data)
        :else nil)
      ;; loop back
      (handle-compiler-output c prj-key current-bld-key))))

(defn- click-once-btn [prj-key]
  (let [prj1 (get-in @state [:projects prj-key])
        checked-builds-keys (checked-builds prj1)
        prj2 (assoc prj1 :state :build-once)
        new-builds (reduce mark-waiting (:builds prj2) checked-builds-keys)
        prj3 (assoc prj2 :builds new-builds)
        current-bld-key (atom nil)]
    ;; show starting state
    (swap! state assoc-in [:projects prj-key] prj3)

    (remove-errors-and-warnings! prj-key)

    ;; start the build
    (let [compiler-chan (exec/build-once prj-key checked-builds-keys)]
      (handle-compiler-output compiler-chan prj-key current-bld-key))))

;; TODO: deal with clean errors
(defn- clean-error [stderr]
  (log "clean error!"))

(defn- clean-success [prj-key]
  ;; set project state
  (swap! state assoc-in [:projects prj-key :state] :idle)

  ;; set builds state
  (doall (map
    #(swap! state assoc-in [:projects prj-key :builds % :status] :missing)
    (keys (get-in @state [:projects prj-key :builds])))))

(defn- click-clean-btn [prj-key]
  (let [prj1 (get-in @state [:projects prj-key])
        prj2 (assoc prj1 :state :clean)
        prj3 (assoc prj2 :builds (mark-builds-for-cleaning (:builds prj2)))]
    ;; show the cleaning state
    (swap! state assoc-in [:projects prj-key] prj3)

    (remove-errors-and-warnings! prj-key)

    ;; start the clean
    (exec/clean prj-key #(clean-success prj-key) clean-error)))

(defn- click-checkbox-header [prj-key]
  (let [p (get-in @state [:projects prj-key])
        num-builds (num-builds p)
        num-selected-builds (selected-builds-count p)]
    (if (zero? num-selected-builds)
      (select-all-builds! prj-key)
      (unselect-all-builds! prj-key))))

(defn- click-build-row [prj-key build-key]
  (swap! state update-in [:projects prj-key :builds build-key :checked?] not))

;;------------------------------------------------------------------------------
;; Sablono Templates
;;------------------------------------------------------------------------------

;; TODO: this is unfinished; I think we should toggle between a date/time
;; display of the last compile and a relative "time ago" display, maybe just by
;; clicking on the field?
(sablono/defhtml last-compile-cell [bld]
  (if (:last-compile-time bld)
    ;;(.from (js/moment (:last-compile-time bld)) (:now bld))
    ;; (str (:last-compile-time bld) " - " (:now bld))
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
    :done-with-error
      [:span.errors-2718a [:i.fa.fa-times] "Compiling failed"]
    :missing
      [:span.missing-f02af [:i.fa.fa-minus-circle] "Output missing"]
    :waiting
      [:span.waiting-e22c3 [:i.fa.fa-clock-o] "Waiting..."]
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
  (str "build-row-fdd97 "
    (if (zero? (mod idx 2))
      "odd-c27e6"
      "even-158a9")
    (if-not checked?
      " not-selected-a8d35")))

(sablono/defhtml build-row [idx [build-key bld] prj-key prj]
  [:tr {:class (build-row-class idx (:checked? bld))
        :on-click (if (= :idle (:state prj))
                    #(click-build-row prj-key build-key))}
    [:td.cell-9ad24 (checked-cell prj bld)]
    [:td.cell-9ad24 (-> bld :cljsbuild :source-paths first)] ;; TODO: print the vector here
    [:td.cell-9ad24 (-> bld :cljsbuild :compiler :output-to)]
    [:td.cell-9ad24 (status-cell bld)]
    [:td.cell-9ad24 (last-compile-cell bld)]
    [:td.cell-9ad24 (-> bld :cljsbuild :compiler :optimizations name)]]
  (when (:error bld)
    (error-row (:error bld)))
  (when (and (:warnings bld)
             (not (zero? (:warnings bld))))
    (map warning-row (:warnings bld))))

;; NOTE: these two functions could be combined

(sablono/defhtml start-auto-btn [prj-key num-builds]
  (if (zero? num-builds)
    [:button.disabled-btn-1884b
      {:disabled true}
      "Start Auto" [:span.count-cfa27 (str "[" num-builds "]")]]
    [:button.btn-da85d
      {:on-click #(click-auto-btn prj-key)}
      "Start Auto" [:span.count-cfa27 (str "[" num-builds "]")]]))

(sablono/defhtml build-once-btn [prj-key num-builds]
  (if (zero? num-builds)
    [:button.disabled-btn-1884b
      {:disabled true}
      "Build Once" [:span.count-cfa27 (str "[" num-builds "]")]]
    [:button.btn-da85d
      {:on-click #(click-once-btn prj-key)}
      "Build Once" [:span.count-cfa27 (str "[" num-builds "]")]]))

(sablono/defhtml checkbox-header [num-selected-builds num-builds]
  (cond
    (= num-selected-builds num-builds) [:i.fa.fa-check-square-o]
    (zero? num-selected-builds) [:i.fa.fa-square-o]
    :else [:i.fa.fa-minus-square-o]))

(sablono/defhtml idle-buttons [prj-key num-selected-builds]
  (start-auto-btn prj-key num-selected-builds)
  (build-once-btn prj-key num-selected-builds)
  [:button.btn-da85d
    {:on-click #(click-clean-btn prj-key)}
    "Clean All"])

(sablono/defhtml auto-buttons [prj-key]
  [:button.btn-da85d
    {:on-click #(click-stop-auto-btn prj-key)}
    "Stop Auto"])

(sablono/defhtml project-status [st]
  (cond
    (= :auto st)
      [:span.status-b8614 "auto compiling..."]
    (= :build-once st)
      [:span.status-b8614 "compiling..."]
    (= :clean st)
      [:span.status-b8614 "deleting files generated by lein-cljsbuild..."]
    :else
      [:span.edit-c0ba4 "edit"]))

;;------------------------------------------------------------------------------
;; Quiescent Components
;;------------------------------------------------------------------------------

;; TODO: should probably make the TableHeader and BuildRow components

(quiescent/defcomponent Project [[prj-key prj]]
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
                (idle-buttons prj-key num-selected-builds)
              (= :auto (:state prj))
                (auto-buttons prj-key))]
          [:div.clr-737fa]]
        [:table.tbl-bdf39
          [:thead
            [:tr.header-row-50e32
              (if (= :idle (:state prj))
                [:th.th-92ca4
                  {:on-click #(click-checkbox-header prj-key)}
                  (checkbox-header num-selected-builds num-builds) "Compile?"]
                [:th.th-92ca4
                  [:i.fa.fa-check.small-check-7b3d7] "Compile?"])
              [:th.th-92ca4 "Source"]
              [:th.th-92ca4 "Output"]
              [:th.th-92ca4 {:style {:width "30%"}} "Status"]
              [:th.th-92ca4 "Last Compile"]
              [:th.th-92ca4 "Optimizations"]]]
          [:tbody
            (map-indexed #(build-row %1 %2 prj-key prj) (:builds prj))]]])))

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

;; TODO: need to queue up RAF requests so we only render the most recent one
(defn- on-change-state [_kwd _the-atom _old-state new-state]
  (let [raf (aget js/window "requestAnimationFrame")
        render-fn (fn []
                    (quiescent/render (AppRoot new-state) (by-id "app")))]
    (raf render-fn)))

(add-watch state :main on-change-state)

;;------------------------------------------------------------------------------
;; Init
;;------------------------------------------------------------------------------

(defn init! []
  ;; trigger the initial render
  (swap! state identity))