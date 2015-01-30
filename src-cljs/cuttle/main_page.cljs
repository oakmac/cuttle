(ns cuttle.main-page
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [clojure.string :refer [blank? join replace split trim]]
    [clojure.walk :refer [keywordize-keys]]
    [cuttle.config :refer [app-data-path config]]
    [cuttle.dom :refer [by-id hide-el! show-el!]]
    [cuttle.exec :as exec]
    [cuttle.projects :as projects :refer [load-project-file]]
    [cuttle.util :refer [
      build-commit build-date current-version date-format file-exists? homedir
      js-log log now on-linux? on-mac? on-windows? uuid write-file-async!]
      :refer-macros [while-let]]
    [cuttle.log :refer [log-info log-warn log-error]]
    goog.events.KeyCodes
    [quiescent :include-macros true]
    [sablono.core :as sablono :include-macros true]))

(def http (js/require "http"))
(def ipc (js/require "ipc"))
(def open (js/require "open"))
(def path (js/require "path"))
(def path-separator (aget path "sep"))

(def ENTER goog.events.KeyCodes.ENTER)
(def ESC goog.events.KeyCodes.ESC)

(def new-project-input-id (uuid))

;;------------------------------------------------------------------------------
;; App State
;;------------------------------------------------------------------------------

;; TODO: move [:projects :order] to :projects-order
(def initial-app-state {
  :add-project-modal-showing? false
  :desktop-notification-on-errors? true
  :desktop-notification-on-warnings? true
  :dock-bounce-on-errors? true
  :dock-bounce-on-warnings? true
  :new-project-dir homedir
  :new-project-error nil
  :new-project-name ""
  :new-project-step 1
  :new-version-bar-showing? false
  :new-version-num nil
  :projects {:order []}
  :settings-modal-showing? false
  :version-tooltip-showing? false
  })

(def state (atom initial-app-state))

(defn- get-ordered-projects
  "Given a project map containing a list of keys in :order, return a vector of
   their ordered values."
  [project-map]
  (let [proj-keys (-> project-map :order)]
    (mapv #(get project-map %) proj-keys)))

;;------------------------------------------------------------------------------
;; Check for Updates
;;------------------------------------------------------------------------------

(def latest-version-url "http://cljs.info/cuttle-latest.json")

;; NOTE: I was sick while writing this function; please don't judge ;)
;; TODO: should do some simple checking that the version number is in a valid
;; format
(defn- check-version2 [new-version]
  (log-info "latest version is" new-version)
  (when (string? new-version)
    (let [arr (split (trim new-version) ".")
          new-major (int (first arr))
          new-minor (int (second arr))
          new-num (+ (* new-major 10000) new-minor)

          arr2 (split (trim current-version) ".")
          our-major (int (first arr2))
          our-minor (int (second arr2))
          our-num (+ (* our-major 10000) our-minor)]
      (when (> new-num our-num)
        (swap! state assoc :new-version-bar-showing? true
                           :new-version-num (trim new-version))))))

(defn- check-version! []
  (log-info "checking for new version")
  (.get http latest-version-url (fn [js-res]
    (let [data (atom "")]
      (.setEncoding js-res "utf8")
      (.on js-res "data" #(swap! data str %))
      (.on js-res "end" (fn []
        (when-let [result (try (.parse js/JSON @data)
                               (catch js/Error _error nil))]
          (check-version2 (aget result "version")))))))))

;;------------------------------------------------------------------------------
;; Project State
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
   :state nil

   ;; from project.clj
   :name ""
   :filename ""
   :version ""
   :license {}
   :dependencies []
   :plugins []

   ;; from project.clj, but with extra tool state
   ;; (see initial-project-build-state)
   :builds {}
   :builds-order []})

;;------------------------------------------------------------------------------
;; Project State Creators
;;------------------------------------------------------------------------------

(def build-keys [:id :source-paths :compiler])

(defn- attach-state-to-build
  "Attach state to project-build map, and prune out unused keys"
  [bld]
  (merge initial-project-build-state
    (select-keys bld build-keys)))

(def project-keys [:filename :name :version :license :dependencies :plugins])

(defn- get-project-build-states
  [prj]
  (let [blds (->> prj :cljsbuild :builds (mapv attach-state-to-build))
        bld-ids (map :id blds)]
    (when blds
      {:builds (zipmap bld-ids blds)
       :builds-order (vec bld-ids)})))

;;------------------------------------------------------------------------------
;; Project Adding and Removing (from app state)
;;------------------------------------------------------------------------------

(defn- conj-if-absent
  [seq- elm]
  (if (some #(= elm %) seq-)
    seq-
    (conj seq- elm)))

(defn- add-project-as-loading!
  [filename]
  (let [project (-> initial-project-state
                    (assoc :state :loading
                           :filename filename))
        new-state (-> @state
                      (assoc-in [:projects filename] project)
                      (update-in [:projects :order] conj-if-absent filename))]
    (reset! state new-state)))

(defn- on-project-load-update!
  [filename project]
  (let [project (-> (select-keys project project-keys)
                    (merge (get-project-build-states project)))]
    (swap! state update-in [:projects filename] merge project)))

(defn- finish-project-load!
  [filename]
  (swap! state assoc-in [:projects filename :state] :idle))

(defn- add-project!
  [filename]
  (log-info "adding project" filename)
  (add-project-as-loading! filename)
  (let [c (load-project-file filename)]
    (go
      (while-let [project (<! c)]
                 (on-project-load-update! filename project))
      (finish-project-load! filename))))

(defn- refresh-project!
  [filename]
  (log-info "refreshing project" filename)
  (add-project! filename))

(defn- init-projects!
  [filenames]
  (doseq [f filenames]
    (add-project! f)))

(defn- remove-project!
  [filename]
  (log-info "removing project" filename)
  (when (contains? (:projects @state) filename)
    (let [new-order (->> @state :projects :order
                     (remove #(= % filename))
                     vec)
          new-state (-> @state
                        (update-in [:projects] dissoc filename)
                        (assoc-in [:projects :order] new-order))]
      (reset! state new-state))))

;;------------------------------------------------------------------------------
;; User-initiated Project Adding and Removing (both workspace and app state)
;;------------------------------------------------------------------------------

(.on ipc "new-project-folder"
  (fn [new-folder]
    (swap! state assoc :new-project-dir new-folder)))

(defn- click-new-project-dir-root []
  (.send ipc "request-new-project-folder-dialog"))

(defn- click-load-existing-project-btn []
  (.send ipc "request-add-existing-project-dialog"))

(.on ipc "add-existing-project-dialog-success"
  (fn [filename]
    (when-let [added? (projects/add-to-workspace! filename)]
      (add-project! filename))
    (swap! state assoc :add-project-modal-showing? false)))

(def delete-confirm-msg (str
  "Remove % from the build tool?\n\n"
  "This action will have no effect on the filesystem."))

(defn- try-remove-project!
  [prj-name filename]
  (log-info "trying to remove project")
  (let [msg (replace delete-confirm-msg "%" prj-name)
        delete? (.confirm js/window msg)]
    (when delete?
      (projects/remove-from-workspace! filename)
      (remove-project! filename))))

;;------------------------------------------------------------------------------
;; Util
;;------------------------------------------------------------------------------

;; NOTE: this function will not work if two builds have the same output-to target
;; should probably refactor with a filter?
(defn- output-to->bld-id [prj-key output-to]
  (let [blds (vals (get-in @state [:projects prj-key :builds]))
        outputs (map #(-> % :compiler :output-to) blds)
        ids (map :id blds)
        m (zipmap outputs ids)]
    (get m output-to)))

(defn- num-selected-builds [prj]
  (->> prj
    :builds
    vals
    (map :active?)
    (remove false?)
    count))

(defn- get-active-bld-ids [prj]
  (->> prj
    :builds
    vals
    (filter #(true? (:active? %)))
    (map :id)))

(defn- letter? [s]
  (and (string? s)
       (.test #"[a-zA-Z]" s)))

;; if dir is "C:\" on Windows, we don't need the extra path separator
(defn- print-dir [dir]
  (str dir
       (when (not= (last dir) path-separator)
          path-separator)))

(defn- validate-new-project [fldr nme]
  (cond
    (blank? nme)
      "Please enter a project name."

    (.test #"[^a-z0-9_-]" nme)
      "Only a-z, hyphens, and underscores please."

    (not (letter? (first nme)))
      "First character must be a letter (a-z)."

    (file-exists? (str fldr path-separator nme))
      "Folder already exists."

    :else nil))

(defn- notify! [title txt]
  (cond
    on-windows?
      (exec/windows-growl-notify! title txt)

    on-linux?
      (exec/linux-notify! title txt)

    :else
      (js/Notification. title (js-obj "body" txt)))

  ;; return nil so it doesn't return the js/Notification object
  nil)

;;------------------------------------------------------------------------------
;; Load Settings
;;------------------------------------------------------------------------------

(defn- load-settings-file!
  "Load settings from file if it exists."
  []
  (let [settings-file (str app-data-path path-separator "settings.json")]
    (when (file-exists? settings-file)
      (log-info "loading settings" settings-file)
      (when-let [settings (-> (js/require settings-file)
                          js->clj
                          keywordize-keys)]
        (swap! state merge settings)))))

(def settings-keys
  #{:dock-bounce-on-errors?
    :dock-bounce-on-warnings?
    :desktop-notification-on-errors?
    :desktop-notification-on-warnings?})

(defn- save-settings!
  "Save settings to disk."
  []
  (let [settings-file (str app-data-path path-separator "settings.json")
        settings (select-keys @state settings-keys)
        settings-json (.stringify js/JSON (clj->js settings))]
    (log-info "saving settings" settings-file)
    (write-file-async! settings-file settings-json)))

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

(defn- clear-compiled-info! [prj-key bld-id]
  (swap! state update-in [:projects prj-key :builds bld-id]
    assoc :last-compile-time nil
          :error nil
          :warnings []))

(defn- remove-compiled-info!
  "Removes compiled info from a build: last-compile-time, error, warnings"
  [prj-key]
  (doall
    (map
      #(clear-compiled-info! prj-key (first %))
      (get-in @state [:projects prj-key :builds]))))

(defn- mark-build-as-waiting! [prj-key bld-id]
  (swap! state assoc-in [:projects prj-key :builds bld-id :state] :waiting))

(defn- mark-builds-as-waiting! [prj-key bld-ids]
  (doall
    (map
      #(mark-build-as-waiting! prj-key %)
      bld-ids)))

(defn- show-start-compiling! [prj-key bld-id]
  (clear-compiled-info! prj-key bld-id)
  (swap! state assoc-in [:projects prj-key :builds bld-id :state] :compiling))

(defn- show-done-compiling! [prj-key bld-id compile-time]
  (let [map-path [:projects prj-key :builds bld-id]
        bld (get-in @state map-path)
        new-state (if (-> bld :warnings empty?) :done :done-with-warnings)
        new-bld (assoc bld :compile-time compile-time
                           :last-compile-time (now)
                           :state new-state)]
    (swap! state assoc-in map-path new-bld)))

(defn- show-warnings! [prj-key bld-id warnings]
  (let [current-state @state]
    (when (and on-mac?
               (:dock-bounce-on-warnings? current-state))
      (.send ipc "bounce-dock"))
    (when (:desktop-notification-on-warnings? current-state)
      (notify! "CLJS Compiler Warning" (first warnings)))
    (swap! state update-in [:projects prj-key :builds bld-id :warnings]
      (fn [w]
        (into [] (concat w warnings))))))

(defn- error-notify-body [error]
  (if (:human-msg error)
    (str (:human-msg error)
         (when (:file error)
           (str " in file " (:file error))))
    (join "\n" (:raw-lines error))))

(defn- show-errors! [prj-key bld-id errors]
  (let [current-state @state]
    (when (and on-mac?
               (:dock-bounce-on-errors? current-state))
      (.send ipc "bounce-dock"))
    (when (:desktop-notification-on-errors? current-state)
      (notify! "CLJS Compiler Error" (error-notify-body errors)))
    (swap! state update-in [:projects prj-key :builds bld-id]
      assoc :error errors
            :state :done-with-error)))

(defn- mark-missing!
  "Mark a build row as missing output if the compiler was halted before finishing."
  [prj-key bld-id]
  (log-info "compiler output missing for" prj-key "-" bld-id)
  (let [state-path [:projects prj-key :builds bld-id :state]
        bld-state (get-in @state state-path)]
    (when (or (= bld-state :waiting)
              (= bld-state :compiling))
      (swap! state assoc-in state-path :missing))))

(defn- compiler-done!
  "Mark a project as being finished with compiling. ie: idle state"
  [prj-key bld-ids]
  (log-info "done compiling " prj-key "-" (pr-str bld-ids))
  (swap! state assoc-in [:projects prj-key :state] :idle)

  ;; any build not marked as "done" at this point is due to compiler error
  ;; or being stopped in the middle of compiling
  (doall (map #(mark-missing! prj-key %) bld-ids)))

(defn- show-warming-up! [prj-key bld-id]
  (swap! state assoc-in [:projects prj-key :builds bld-id :state] :warming-up))

;;------------------------------------------------------------------------------
;; Compiler Interface
;;------------------------------------------------------------------------------

;; NOTE: this could be simplified with a go-loop?
;; TODO: "current-bld-id" should probably be in exec.cljs and passed through
;; the channel
(defn- handle-compiler-output
  "This function reads from the console output channel and updates the UI.
   NOTE: recursive function, terminating case is when the channel is closed"
  [c prj-key bld-ids current-bld-id first-output?]
  (go
    (when-let [[type data] (<! c)]

      (when (:log-compiler-output config)
        (js-log "Channel contents:")
        (log type)
        (log data)
        (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"))

      (when @first-output?
        (mark-builds-as-waiting! prj-key bld-ids)
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
          (compiler-done! prj-key bld-ids)

        :error
          (show-errors! prj-key @current-bld-id data)

        nil)

      ;; loop back
      (handle-compiler-output c prj-key bld-ids current-bld-id first-output?))))

;; NOTE: start-auto-compile! and start-compile-once! should probably be combined

(defn- start-auto-compile! [prj-key bld-ids]
  ;; show project loading state
  (swap! state assoc-in [:projects prj-key :state] :auto)

  ;; update the BuildRows state
  (doall (map #(show-warming-up! prj-key %) bld-ids))

  (remove-compiled-info! prj-key)

  ;; start the build
  (let [compiler-chan (exec/start-auto prj-key bld-ids)]
    (handle-compiler-output compiler-chan prj-key bld-ids (atom nil) (atom true))))

(defn- start-compile-once! [prj-key bld-ids]
  ;; show project state
  (swap! state assoc-in [:projects prj-key :state] :once)

  ;; update the BuildRows state
  (doall (map #(show-warming-up! prj-key %) bld-ids))

  (remove-compiled-info! prj-key)

  ;; start the build
  (let [compiler-chan (exec/build-once prj-key bld-ids)]
    (handle-compiler-output compiler-chan prj-key bld-ids (atom nil) (atom true))))

;;------------------------------------------------------------------------------
;; Events
;;------------------------------------------------------------------------------

(defn- click-maybe-later []
  (swap! state assoc :new-version-bar-showing? false))

;; TODO: this will not work if they click outside the app root element
;; need to refactor
(defn- click-root []
  (doall
    (map
      (fn [prj-key]
        (swap! state assoc-in [:projects prj-key :compile-menu-showing?] false))
      (-> @state :projects :order))))

(defn- compile-now! [prj-key prj bld-ids]
  (if (:auto-compile? prj)
    (start-auto-compile! prj-key bld-ids)
    (start-compile-once! prj-key bld-ids)))

(defn- mark-build-as-cleaning! [prj-key bld-id]
  (swap! state assoc-in [:projects prj-key :builds bld-id :state] :cleaning))

(defn- start-build-clean! [prj-key bld-id]
  (let [bld (get-in @state [:projects prj-key :builds bld-id])]
    (mark-build-as-cleaning! prj-key bld-id)
    (exec/clean-build! prj-key bld)))

(defn- click-compile-btn [prj-key]
  (log-info "clicked compile button for" prj-key)
  (let [prj (get-in @state [:projects prj-key])
        bld-ids (get-active-bld-ids prj)]
    ;; safeguard
    (when-not (zero? (count bld-ids))
      ;; remove any errors / warnings from previous builds
      (remove-compiled-info! prj-key)

      ;; update project state
      (swap! state assoc-in [:projects prj-key :state] :cleaning)

      ;; clean the builds
      (doall (map #(start-build-clean! prj-key %) bld-ids))

      ;; start the compile
      (compile-now! prj-key prj bld-ids))))

(defn- click-stop-auto-btn [prj-key]
  (log-info "clicked stop compile for" prj-key)

  ;; stop the process
  (exec/stop-auto! prj-key)

  ;; update project state
  ;; NOTE: the project state gets updated in (compiler-done!), but it doesn't
  ;; hurt to have it here too
  (swap! state assoc-in [:projects prj-key :state] :idle))

(defn- click-compile-options [js-evt prj-key]
  (log-info "toggling compile option visibility for" prj-key)
  (.stopPropagation js-evt)
  (swap! state update-in [:projects prj-key :compile-menu-showing?] not))

(defn- toggle-auto-compile [prj-key]
  (log-info "toggling auto compile for" prj-key)
  (swap! state update-in [:projects prj-key :auto-compile?] not))

(defn- toggle-build-active [prj-key bld-id]
  (log-info "toggling build active for" bld-id "on" prj-key)
  (swap! state update-in [:projects prj-key :builds bld-id :active?] not))

(defn- show-new-project-modal []
  (log-info "showing add project modal")
  (swap! state assoc :add-project-modal-showing? true
                     :new-project-step 1))

(defn- close-add-project-modal []
  (log-info "closing add project modal")
  (swap! state assoc :add-project-modal-showing? false))

;; TODO: change the new-project-step's to keywords instead of magic numbers

(defn- click-new-project-btn []
  (log-info "starting new project dialog")
  (swap! state assoc :new-project-dir homedir
                     :new-project-error nil
                     :new-project-name ""
                     :new-project-step 2))

(defn- create-new-project! [fldr nme]
  (let [lein-file (str fldr path-separator nme path-separator "project.clj")]
    ;; kick off the new project
    (exec/new-project fldr nme (fn []
      (swap! state assoc :add-project-modal-showing? false)
      (projects/add-to-workspace! lein-file)
      (add-project! lein-file)))

    ;; go to the next step
    (swap! state assoc :new-project-step 3)))

(defn- click-create-project-btn []
  (let [current-state @state
        fldr (:new-project-dir current-state)
        nme (:new-project-name current-state)
        errors (validate-new-project fldr nme)]
    (if errors
      (swap! state assoc :new-project-error errors)
      (create-new-project! fldr nme))))

(defn- on-change-new-project-name-input [js-evt]
  (let [new-name (-> (aget js-evt "currentTarget" "value")
                     (.toLowerCase)
                     (replace " " "-"))]
    (swap! state assoc :new-project-name new-name)))

(defn- click-go-back-btn []
  (swap! state assoc :new-project-step 1))

(def releases-url "https://github.com/oakmac/cuttle/releases")

(defn- click-show-me-btn []
  (open releases-url))

(defn- click-open-project-folder!
  [filename]
  (log-info "opening project folder for" filename)
  (let [dirname (.dirname path filename)]
    (open dirname)))

(defn- click-add-project-modal-overlay []
  ;; do not let them close the modal while a new project is being created
  (when-not (= 3 (:new-project-step @state))
    (close-add-project-modal)))

(defn- click-settings-modal-overlay []
  (swap! state assoc :settings-modal-showing? false))

(defn- on-keydown-new-project-name [js-evt]
  (when (= ENTER (aget js-evt "keyCode"))
    (click-create-project-btn)))

(defn- on-mount-new-project-form
  "Put the cursor focus on the input field when we mount this component."
  []
  (when-let [el (by-id new-project-input-id)]
    (.focus el)))

(defn- click-settings-link []
  (log-info "opening settings modal")
  (swap! state assoc :settings-modal-showing? true))

;; TODO:
;; taskbar flash on errors
;; taskbar flash on warnings

;; TODO: would probably be better to save settings once on app close than
;; everytime they change; this is fine for now

(defn- click-dock-bounce-on-errors []
  (swap! state update-in [:dock-bounce-on-errors?] not)
  (save-settings!))

(defn- click-dock-bounce-on-warnings []
  (swap! state update-in [:dock-bounce-on-warnings?] not)
  (save-settings!))

(defn- click-desktop-notification-on-errors []
  (swap! state update-in [:desktop-notification-on-errors?] not)
  (save-settings!))

(defn- click-desktop-notification-on-warnings []
  (swap! state update-in [:desktop-notification-on-warnings?] not)
  (save-settings!))

(defn- on-mouse-enter-tooltip []
  (swap! state assoc :version-tooltip-showing? true))

(defn- on-mouse-leave-tooltip []
  (swap! state assoc :version-tooltip-showing? false))

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
      [:span [:i.fa.fa-cog.fa-spin] "Cleaning"]
    :compiling
      [:span.compiling-9cc92 [:i.fa.fa-cog.fa-spin] "Compiling"]
    :done
      [:span.success-5c065
        [:i.fa.fa-check] (str "Done in " compile-time " seconds")]
    :done-with-warnings
      [:span.with-warnings-4b105
        [:i.fa.fa-exclamation-triangle] (warnings-state (count warnings))]
    :done-with-error
      [:span.errors-2718a [:i.fa.fa-times] "Compiling failed"]
    :warming-up
      [:span [:i.fa.fa-cog.fa-spin] "Warming up"]
    :missing
      [:span [:i.fa.fa-minus-circle] "Output missing"]
    :waiting
      [:span [:i.fa.fa-clock-o] "Waiting"]
    "*unknown state*"))

(sablono/defhtml error-row [error]
  [:tr.error-row-b3028
    [:td.error-cell-1ccea {:col-span "6"}
      [:i.fa.fa-times]
      (error-notify-body error)]])

(sablono/defhtml warning-row [w]
  [:tr.warning-row-097c8
    [:td.warning-cell-b9f12 {:col-span "6"}
      [:i.fa.fa-exclamation-triangle] w]])

(sablono/defhtml loading-builds-row []
  [:tr
   [:td.load-builds-1b35d {:col-span 6}
    [:i.fa.fa-cog] ;; can't add ".fa-spin" without a weird border bug occurring
    "Loading builds..."]])

(sablono/defhtml no-builds-row []
  [:tr
   [:td.no-builds-2295f {:col-span 6}
    [:i.fa.fa-exclamation-triangle]
    "No builds found in this project."]])

(defn- build-row-class [{:keys [idx active?]}]
  (str "build-row-fdd97 "
    (if (zero? (mod idx 2))
      "odd-c27e6"
      "even-158a9")
    (if-not active?
      " not-active-a8d35")))

(sablono/defhtml build-option [bld prj-key]
  [:div.bld-e7c4d
    {:on-click #(toggle-build-active prj-key (:id bld))}
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
    (map
      (fn [bld-id]
        (let [bld (get-in prj [:builds bld-id])]
          (build-option bld prj-key)))
      (:builds-order prj))])

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
  (when (:compile-menu-showing? prj)
    (compile-menu prj-key prj)))

(sablono/defhtml auto-state [prj-key]
  [:span.status-984ee "Auto compiling..."]
  [:button.btn-da85d
    {:on-click #(click-stop-auto-btn prj-key)}
    "Stop"])

(sablono/defhtml cleaning-state [prj-key]
  [:span.status-984ee "Removing generated files..."])

(sablono/defhtml once-state [prj-key]
  [:span.status-984ee "Compiling..."])

(sablono/defhtml no-projects []
  [:div.outer-f80bb
    [:div.inner-aa3fc
      [:h4 "No active projects."]
      [:p "Would you like to "
        [:span.link-e7e58 {:on-click show-new-project-modal}
          "add one"] "?"]]])

(sablono/defhtml modal-overlay [click-fn]
  [:div.modal-overlay-120d3 {:on-click click-fn}])

(sablono/defhtml new-or-existing-project []
  [:div.modal-body-fe4db
    [:div.modal-chunk-2041a
      [:button.big-btn-a5d18 {:on-click click-new-project-btn}
        "New Project"]
      [:p.small-info-b72e9
        "Create a new project from scratch."]]
    [:div.modal-chunk-2041a
      [:button.big-btn-a5d18 {:on-click click-load-existing-project-btn}
        "Existing Project"]
      [:p.small-info-b72e9
        "Load an existing project from a Leiningen " [:code "project.clj"] " file."]]
    [:div.modal-bottom-050c3
      [:span.bottom-link-7d8d7 {:on-click close-add-project-modal}
        "close"]]])

(sablono/defhtml new-project-form [app-state]
  [:div.modal-body-fe4db
    [:div.modal-chunk-2041a
      [:label.label-b0246 "Project Name"]
      [:input.text-input-4800e
        {:id new-project-input-id
         :on-change on-change-new-project-name-input
         :on-key-down on-keydown-new-project-name
         :type "text"
         :value (:new-project-name app-state)}]]
    [:div.modal-chunk-2041a
      [:label.label-b0246 "Project Folder"]
      [:div
        [:span.link-e7e58 {:on-click click-new-project-dir-root}
          (print-dir (:new-project-dir app-state))]
        (:new-project-name app-state)]]
    (when (:new-project-error app-state)
      [:div.error-cdef1
        [:i.fa.fa-exclamation-triangle]
        (:new-project-error app-state)])
    [:div.modal-bottom-050c3
      [:button.primary-d0cd0 {:on-click click-create-project-btn}
        "Create Project"]
      [:span.bottom-link-7d8d7 {:on-click click-go-back-btn}
        "go back"]]])

(sablono/defhtml creating-new-project [modal-state]
  [:div.modal-body-fe4db
    [:div.modal-chunk-2041a.creating-6d31a
      [:i.fa.fa-cog.fa-spin.icon-e70fb]
      "Creating project " [:strong (:new-project-name modal-state)]]])

;; TODO: implement this, GitHub Issue #46
; (sablono/defhtml new-project-step-4 []
;   [:div.modal-body-fe4db
;     [:div.modal-chunk-2041a
;       [:h4.success-hdr-0f1c6 "Success!"]]
;     [:div.modal-chunk-2041a
;       "*TODO: details of the new project*"]
;     [:div.modal-bottom-050c3
;       [:button {:on-click close-add-project-modal} "Got it!"]]])

;; TODO: make this a quiescent component
(sablono/defhtml new-version-bar [new-version]
  [:div.info-bar-b38b4
    [:i.fa.fa-info-circle]
    (str "A new version of Cuttle (v" new-version ") is available!")
    [:button.show-me-btn-16a12
      {:on-click click-show-me-btn}
      "Show me"]
    [:span.ignore-link-ff917
      {:on-click click-maybe-later}
      "Maybe later"]])

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
            [:span.project-icons-dd1bb
              [:i.fa.fa-folder-open-o.project-icon-1711d
                {:title "Open Folder"
                 :on-click #(click-open-project-folder! prj-key)}]
              (when (= (:state prj) :idle)
                (list
                  ;; NOTE: hiding edit link for now
                  ;; [:i.fa.fa-edit.project-icon-1711d]
                  [:i.fa.fa-refresh.project-icon-1711d
                   {:title "Refresh the project.clj file"
                    :on-click #(refresh-project! prj-key)}]
                  [:i.fa.fa-times.project-icon-1711d
                    {:title "Remove"
                     :on-click #(try-remove-project! (:name prj) prj-key)}]))]]
          [:div.right-f5656
            (case (:state prj)
              :loading nil
              :auto (auto-state prj-key)
              :cleaning (cleaning-state prj-key)
              :idle (idle-state prj-key prj)
              :once (once-state prj-key)
              "*unknown project state*")]]
        [:table.tbl-bdf39
         (bld-tbl-hdr)
          (if (= (:state prj) :loading)
            (loading-builds-row)
            (if (empty? (:builds prj))
              (no-builds-row)
              (map-indexed
                (fn [idx bld-id]
                  (let [bld (get-in prj [:builds bld-id])]
                    (BuildRow (assoc bld :idx idx
                                     :prj-key prj-key))))
                (:builds-order prj))))]])))

(quiescent/defcomponent NewProjectForm [modal-state]
  (quiescent/on-mount
    (new-project-form modal-state)
    on-mount-new-project-form))

(quiescent/defcomponent AddProjectModal [modal-state]
  (let [current-step (:new-project-step modal-state)]
    (case current-step
      1 (new-or-existing-project)
      2 (NewProjectForm modal-state)
      3 (creating-new-project modal-state)
      nil)))

(quiescent/defcomponent SettingsModal [app-state]
  (sablono/html
    [:div.modal-body-8c212
      [:h4.modal-title-8e35b "Settings"]
      (when on-mac? (list
        [:label.settings-label-7daea
          {:on-click click-dock-bounce-on-errors}
          (if (:dock-bounce-on-errors? app-state)
            [:i.fa.fa-check-square-o]
            [:i.fa.fa-square-o])
          "Dock bounce on errors."]
        [:label.settings-label-7daea
          {:on-click click-dock-bounce-on-warnings}
          (if (:dock-bounce-on-warnings? app-state)
            [:i.fa.fa-check-square-o]
            [:i.fa.fa-square-o])
          "Dock bounce on warnings."]
        [:div.spacer-586a6]))
      [:label.settings-label-7daea
        {:on-click click-desktop-notification-on-errors}
        (if (:desktop-notification-on-errors? app-state)
          [:i.fa.fa-check-square-o]
          [:i.fa.fa-square-o])
        "Desktop notifications on errors."]
      [:label.settings-label-7daea
        {:on-click click-desktop-notification-on-warnings}
        (if (:desktop-notification-on-warnings? app-state)
          [:i.fa.fa-check-square-o]
          [:i.fa.fa-square-o])
        "Desktop notifications on warnings."]]))

(quiescent/defcomponent Header [version-tooltip-showing?]
  (sablono/html
    [:div.header-a4c14
      [:img.logo-0a166 {:src "img/cuttle-logo.svg"}]
      [:div.title-8749a
        "Cuttle"
        [:span.version-8838a
          {:on-mouse-enter on-mouse-enter-tooltip
           :on-mouse-leave on-mouse-leave-tooltip}
          (str "v" current-version)]]
      (when version-tooltip-showing?
        [:div.tooltip-aca75
          [:table
            [:tr
              [:td.label-be32b "Version"]
              [:td.value-aee48 current-version]]
            [:tr
              [:td.label-be32b "Released"]
              [:td.value-aee48 build-date]]
            [:tr
              [:td.label-be32b "Commit"]
              [:td.value-aee48.mono-cd368 (subs build-commit 0 10)]]]])
      [:div.title-links-42b06
        [:span.link-3d3ad
          {:on-click click-settings-link}
          [:i.fa.fa-cog] "Settings"]
        [:span.link-3d3ad
          {:on-click show-new-project-modal}
          [:i.fa.fa-plus] "Add project"]]
      [:div.clr-737fa]]))

(def add-project-modal-keys
  "These are all the keys we need in order to build the Add Project modal."
  #{:new-project-dir
    :new-project-error
    :new-project-name
    :new-project-step })

(def settings-modal-keys
  "All the keys we need in order to build the Settings modal."
  #{:dock-bounce-on-errors?
    :dock-bounce-on-warnings?
    :desktop-notification-on-errors?
    :desktop-notification-on-warnings? })

(quiescent/defcomponent AppRoot [app-state]
  (sablono/html
    [:div
      (when (:add-project-modal-showing? app-state)
        (list (modal-overlay click-add-project-modal-overlay)
              (AddProjectModal (select-keys app-state add-project-modal-keys))))
      (when (:settings-modal-showing? app-state)
        (list (modal-overlay click-settings-modal-overlay)
              (SettingsModal (select-keys app-state settings-modal-keys))))
      (when (:new-version-bar-showing? app-state)
        (new-version-bar (:new-version-num app-state)))
      [:div.app-ca3cd {:on-click click-root}
        (Header (:version-tooltip-showing? app-state))
        (let [projects (-> app-state :projects get-ordered-projects)]
          (if (zero? (count projects))
            (no-projects)
            (map Project projects)))]]))

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
  (let [render-fn #(quiescent/render (AppRoot new-state) (by-id "mainPage"))]
    (set! anim-frame-id (request-anim-frame render-fn))))

(add-watch state :main on-change-state)

;;------------------------------------------------------------------------------
;; Non-react Events
;;------------------------------------------------------------------------------

(defn- on-keydown-body [js-evt]
  (let [key-code (aget js-evt "keyCode")
        escape-key-pressed? (= ESC key-code)
        current-state @state]
    (when escape-key-pressed?
      (when (:add-project-modal-showing? current-state)
        (click-add-project-modal-overlay))
      (when (:settings-modal-showing? current-state)
        (click-settings-modal-overlay)))))

(def events-added? (atom false))

(defn- add-events!
  "Add non-react events.
   NOTE: this is a run-once function"
  []
  (when-not @events-added?
    (.addEventListener js/document.body "keydown" on-keydown-body)
    (reset! events-added? true)))

;;------------------------------------------------------------------------------
;; Init
;;------------------------------------------------------------------------------

(defn- show-main-page! []
  (hide-el! "loadingPage")
  (hide-el! "shutdownPage")
  (show-el! "mainPage"))

(defn init! [proj-filenames]
  (log-info "initializing main page")
  (add-events!)
  (load-settings-file!)
  (init-projects! proj-filenames)
  (show-main-page!)

  ;; trigger initial UI render even if proj-filenames is empty
  ;; TODO: probably should change the way init-projects! works
  (swap! state identity)

  ;; check for updates if we are not in dev mode
  (when (neg? (.indexOf current-version "DEV"))
    (check-version!)))
