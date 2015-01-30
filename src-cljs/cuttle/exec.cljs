(ns cuttle.exec
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.reader :refer [read-string]]
    [clojure.string :refer [join replace split-lines split trim]]
    [cljs.core.async :refer [chan close! put!]]
    [cuttle.config :refer [config]]
    [cuttle.util :refer [file-exists? js-log log on-windows? path-join
                         windows-bin-dir uuid]]
    [cuttle.log :refer [log-info log-warn log-error]]))

(declare extract-target-from-start-msg parse-java-version)

;;------------------------------------------------------------------------------
;; Require Modules
;;------------------------------------------------------------------------------

(def child-proc (js/require "child_process"))
(def fs (js/require "fs-extra"))
(def js-exec (aget child-proc "exec"))
(def js-spawn (aget child-proc "spawn"))
(def path (js/require "path"))
(def path-separator (aget path "sep"))

;;------------------------------------------------------------------------------
;; Lein
;;------------------------------------------------------------------------------

(defn- lein-path
  "Get path to our packaged leiningen script."
  []
  (if on-windows?
    (path-join windows-bin-dir "lein.bat")
    (-> (path-join js/__dirname "bin" "lein")
        (replace " " "\\ "))))

(defn lein
  "Make lein command string"
  [args]
  (str (lein-path) " with-profile +cuttle " args))

;;------------------------------------------------------------------------------
;; Shell Escape Util
;;------------------------------------------------------------------------------

;; NOTE: there is probably already a very robust library for doing this sort
;; of thing

(defn- windows-shell-escape [s]
  (replace s "\"" "\\\""))

(defn- unix-shell-escape [s]
  (replace s "'" "\\'"))

;;------------------------------------------------------------------------------
;; Check for Java
;;------------------------------------------------------------------------------

(defn correct-java-installed?
  []
  (log-info "checking java version")
  (let [out-chan (chan)
        cmd "java -version"
        callback-fn
        (fn [error stdout stderr]
          (if error
            (do
              (log-info "couldn't run java -version")
              (put! out-chan false))
            (let [version (parse-java-version stderr)
                  valid? (>= version 7)]
              (log-info "found java version:" version "from" stderr)
              (put! out-chan valid?))))]
    (js-exec cmd callback-fn)
    out-chan))

;;------------------------------------------------------------------------------
;; Lein profile for dependencies
;;------------------------------------------------------------------------------

(def lein-profile
  "Lein profile containing dependencies required for our tool."
  '{:cuttle {:plugins [[lein-pprint "1.1.1"]]}})

(defn add-lein-profile!
  "Adds our Lein profile to the user's global profiles.clj."
  []
  (log-info "adding cuttle's global lein profile")
  (let [dir (aget js/global "__dirname")
        jar (path-join dir "bin" "add-lein-profile.jar")
        cmd (str "java -jar " jar " '" (pr-str lein-profile) "'")
        out-chan (chan)]
    (log-info "running" cmd)
    (js-exec cmd #(close! out-chan))
    out-chan))

(defn get-cljsbuild-with-profiles
  [profile-path]
  (log-info "checking" profile-path "for cljsbuild config in :dev profile")
  (let [out-chan (chan)
        cmd (lein "pprint :cljsbuild")
        js-options (js-obj "cwd" profile-path)
        callback (fn [error stdout stderr]
                   (let [project (read-string stdout)]
                     (put! out-chan (or project {}))))]
    (js-exec cmd js-options callback)
    out-chan))

;;------------------------------------------------------------------------------
;; Determine Compiler Output Line Type
;;------------------------------------------------------------------------------

;; NOTE: this probably belongs in it's own library or emitted as EDN from the
;; compiler itself
;; we can get by with regex and duct tape for now ;)

(defn- red-line? [s]
  (.test #"\[31m" s))

(defn- error-line? [s]
  (or (red-line? s)
      (.test #"clojure\.lang\.ExceptionInfo" s)))

(def ^:private stopped-signal (str "*** OUTPUT STOPPED *** " (uuid)))

(defn- end-line? [s]
  (or (.test #"Subprocess failed" s)
      (= s stopped-signal)))

(defn- success-line? [s]
  (and (.test #"Successfully compiled" s)
       (.test #"seconds\." s)))

(defn- warning-line? [s]
  (.test #"^WARNING: " s))

(defn- start-line? [s]
  (and (.test #"Compiling " s)
       (.test #"]\.\.\.$" s)))

(defn- determine-line-type
  "Returns the type of line output from the compiler.
   nil if we do not recognize the line or don't care what it is"
  [s]
  (cond
    (error-line? s)   :error
    (end-line? s)     :end-output
    (success-line? s) :success
    (warning-line? s) :warning
    (start-line? s)   :start
    :else nil))

;;------------------------------------------------------------------------------
;; Extract Info From Lines
;;------------------------------------------------------------------------------

(defn- extract-time-from-success-msg [s]
  (-> s
      (replace #"^.+ in " "")
      (replace #" seconds.+$" "")
      float))

(defn- clean-warning-line [s]
  (-> s
      (replace "WARNING: " "")
      trim))

(defn- extract-warning-msgs [s]
  (->> s
      split-lines
      (map clean-warning-line)
      (into [])))

(defn- extract-file-from-error-line [s]
  (-> s
      (replace #"^.*failed compiling file:" "")
      (replace #" \{:.*$" "")))

(defn- has-file-info? [l]
  (.test #"failed compiling file\:" l))

(defn- caused-by-line? [l]
  (.test #"^Caused by:" l))

(defn- extract-error-file
  "Returns the file that has the error (or nil) from error lines."
  [lines]
  (reduce (fn [v l]
    (if (has-file-info? l)
      (extract-file-from-error-line l)
      v))
    nil lines))

(defn- extract-human-error* [s]
  (-> s
      (replace #"^Caused by: clojure\.lang\.ExceptionInfo: " "")
      (replace #" \{:.*$" "")))

(defn- extract-human-error
  "Returns a human-readable error message (or nil) from error lines."
  [lines]
  (reduce (fn [v l]
    (if (caused-by-line? l)
      (extract-human-error* l)
      v))
    nil lines))

(defn- has-line-info? [s]
  (.test #"line \d+" s))

;; NOTE: some lines contain more than one line number, from my small sample size
;; it seems like we always want the first one
(defn- extract-line-number* [s]
  (-> s
      ;; NOTE: native JS .replace here, not clojure.string/replace
      ;; I'm not sure if clojure.string/replace does capture groups?
      (.replace #"(^.+line )(\d+)(.*$)" "$2")
      int))

(defn- extract-line-number [lines]
  (reduce (fn [v l]
    (if (and (has-line-info? l)
             (nil? v))
      (extract-line-number* l)
      v))
    nil lines))

(defn- extract-error-info
  "Extract a map of error information from a vector of error lines."
  [lines]
  {:file (extract-error-file lines)
   :line (extract-line-number lines)
   :human-msg (extract-human-error lines)
   :raw-lines lines })

(defn- clean-line
  "Clean bash escape characters from a console output line."
  [s]
  (-> s
      (replace #"\033" "")    ;; remove escape characters
      (replace #"\[\dm" "")   ;; remove color codes
      (replace #"\[\d\dm" "")
      trim))

(defn- on-console-line
  "Handles each line from the compiler console output.
   It parses them using regex and puts the results onto a core.async channel."
  [raw-line return-chan inside-error? err-msg-buffer]
  (let [line-type (determine-line-type raw-line)
        cleaned-line (clean-line raw-line)]

    (when (:log-compiler-output config)
      (js-log raw-line)
      (js-log cleaned-line)
      (when line-type
        (log (str "##### line type: " line-type)))
      (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"))

    ;; start an error sequence
    (when (and (not @inside-error?)
               (= line-type :error))
      (reset! inside-error? true)
      (reset! err-msg-buffer []))

    ;; collect error messages
    (when (and @inside-error?
               (= line-type :error))
      (swap! err-msg-buffer conj cleaned-line))

    ;; close an error sequence
    (when (and @inside-error?
               line-type
               (not= line-type :error))
      (reset! inside-error? false)
      (put! return-chan [:error (extract-error-info @err-msg-buffer)]))

    ;; start compiling signal
    (when (= line-type :start)
      (put! return-chan [:start (extract-target-from-start-msg raw-line)]))

    ;; compilation success
    (when (= line-type :success)
      (put! return-chan [:success (extract-time-from-success-msg raw-line)]))

    ;; warnings
    (when (= line-type :warning)
      (put! return-chan [:warning (extract-warning-msgs raw-line)]))))

(defn- on-console-output
  "This function gets called with chunks of text from the compiler console output.
   We split it on newlines and then handle each line individually."
  [raw-output return-chan inside-error? err-msg-buffer stopped-output-timeout]

  ; (js-log "raw output:")
  ; (js-log raw-output)
  ; (js-log "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")

  ;; clear timeout whenever we receive a new chunk
  (js/clearTimeout @stopped-output-timeout)

  ;; parse every line of the output
  (doall
    (map
      #(on-console-line % return-chan inside-error? err-msg-buffer)
      (split-lines raw-output)))

  ;; Set a timeout to indicate the output has stopped 50ms after the last chunk
  ;; is received.
  ;; NOTE: this is used to determine when the output has "stopped" so we can
  ;; close the error sequence
  (let [t (js/setTimeout
            #(on-console-line stopped-signal return-chan inside-error? err-msg-buffer)
            50)]
    (reset! stopped-output-timeout t)))

(defn- on-close-child [c]
  (put! c [:finished])
  (close! c))

;;------------------------------------------------------------------------------
;; Helper
;;------------------------------------------------------------------------------

(defn- project-file->cwd [f]
  (replace f #"project\.clj$" ""))

(defn- project-file? [f]
  (.test #"project\.clj$" f))

;; TODO: this function needs a better name
;; also we should probably do some checking on valid cwd format
(defn- convert-cwd [cwd]
  (if (project-file? cwd)
    (project-file->cwd cwd)
    cwd))

;; NOTE: this function breaks on Windows when "cmd" has spaces: something to do
;; with the weird rules for escaping when using "cmd /c"
;; This is why we copy lein.bat and lein.jar to C:\cuttle-bin\ on app load
;; and call them using windows-bin-dir
;; more information:
;; https://github.com/joyent/node/issues/2318
;; https://github.com/oakmac/cuttle/issues/73
(defn- spawn [cmd cwd]
  (if on-windows?
    (js-spawn "cmd" (array "/c" cmd) (js-obj "cwd" cwd))
    (let [cmd-arr (split cmd #" ")]
      (js-spawn (first cmd-arr)
        (apply array (rest cmd-arr))
        (js-obj "cwd" cwd)))))

(defn- convert-ps-line [l]
  (let [l-arr (split (trim l) #"\s+")
        ;; NOTE: This is dependent upon the order of the options passed to
        ;; the -o flag of ps in kill-auto-on-unix
        pid  (-> l-arr first int)
        ppid (-> l-arr second int)]
    [pid ppid]))

(defn- kill-auto-on-unix2 [ppid output callback-fn]
  (let [lines1 (split-lines output)
        lines2 (map convert-ps-line lines1)
        pid-to-kill (ffirst (filter #(= ppid (second %)) lines2))]
    ;; sanity check to make sure the pid exists
    (when (pos? pid-to-kill)
      (js-exec (str "kill " pid-to-kill) callback-fn))))

;; I fought with this for hours re: trying to kill the process from node.js
;; this feels hacky, but it works fine
;; TODO: write a general-purpose "js-exec" function that returns a core.async
;; channel; would prefer that to using callbacks here
(defn- kill-auto-on-unix [pid callback-fn]
  (let [child (js-spawn "ps" (array "-eo" "pid,ppid"))]
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stdout child) "data" #(kill-auto-on-unix2 pid % callback-fn))))

;;------------------------------------------------------------------------------
;; Public Methods
;;------------------------------------------------------------------------------

(def auto-pids (atom {}))

(defn start-auto
  "Start auto-compile. This function returns a core.async channel."
  [prj-key bld-ids]
  (let [c (chan)
        lein-cmd (str "cljsbuild auto " (join " " bld-ids))
        child (spawn (lein lein-cmd) (convert-cwd prj-key))
        inside-error? (atom false)
        err-msg-buffer (atom "")
        stopped-output-timeout (atom nil)]
    (log-info "starting auto-compile for" prj-key "builds" (pr-str bld-ids))
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on child "close" #(on-close-child c))

    ;; save the child pid
    (swap! auto-pids assoc prj-key (aget child "pid"))

    ;; return the channel
    c))

(defn stop-auto!
  "Kill an auto-compile process."
  ([prj-key]
    (stop-auto! prj-key (fn [] nil)))
  ([prj-key callback-fn]
    (let [main-pid (get @auto-pids prj-key)]
      (log-info "trying to stop auto-compile for" prj-key "at pid" main-pid)
      (if on-windows?
        (js-exec (str "taskkill /pid " main-pid " /T /F") callback-fn)
        (kill-auto-on-unix main-pid callback-fn))

      ;; remove the pid from the atom
      (swap! auto-pids dissoc prj-key))))

(defn kill-all-leiningen-instances! []
  (let [currently-running-prj-keys (keys @auto-pids)
        num-running (count currently-running-prj-keys)
        num-finished (atom 0)
        ch (chan)
        callback-fn (fn []
                      (swap! num-finished inc)
                      (when (= num-running @num-finished)
                        (put! ch :all-finished)))]
    (log-info "killing lein processes for" (pr-str currently-running-prj-keys))
    (doall
      (map #(stop-auto! % callback-fn) currently-running-prj-keys))
    ;; close the channel immediately if there are no running processes
    (when (zero? num-running)
      (put! ch :all-finished))
    ch))

(defn build-once
  "Start the build once process. This function returns a core.async channel
   that receives the status of the build.
   The channel is closed when the build is finished."
  [prj-key bld-ids]
  (let [c (chan)
        lein-cmd (str "cljsbuild once " (join " " bld-ids))
        child (spawn (lein lein-cmd) (convert-cwd prj-key))
        inside-error? (atom false)
        err-msg-buffer (atom "")
        stopped-output-timeout (atom nil)]
    (log-info "building once for" prj-key "builds" (pr-str bld-ids))
    (.setEncoding (.-stderr child) "utf8")
    (.setEncoding (.-stdout child) "utf8")
    (.on (.-stderr child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on (.-stdout child) "data"
      #(on-console-output % c inside-error? err-msg-buffer stopped-output-timeout))
    (.on child "close" #(on-close-child c))
    ;; return the channel
    c))

;; TODO: "target" here is probably configurable in build; need to look closer
;; at Leiningen's code
(defn clean-build! [prj-key bld]
  (log-info "cleaning build for" prj-key)
  (let [cwd (convert-cwd prj-key)
        output-dir (-> bld :compiler :output-dir)
        output-dir-full (str cwd "target" output-dir)
        output-to (-> bld :compiler :output-to)
        output-to-full (str cwd output-to)]
    (when (and output-dir
               (file-exists? output-dir-full))
      (.removeSync fs output-dir-full))
    (when (and output-to
               (file-exists? output-to-full))
      (.removeSync fs output-to-full))))

(defn new-project [folder-name project-name callback-fn]
  (log-info "creating a new project" project-name "at" project-name)
  (let [lein-cmd (lein (str "new mies " project-name))]
    (js-exec lein-cmd (js-obj "cwd" folder-name) callback-fn)))

(def cuttle-icon (str
  (aget js/global "__dirname")
  path-separator
  "img"
  path-separator
  "cuttle-logo.png"))

(defn linux-notify! [title message]
  (log-info "trying to notify linux" title "-" message)
  (let [cmd (str "notify-send "
                 "--icon='" cuttle-icon "' "
                 "'" (unix-shell-escape title) "' "
                 "'" (unix-shell-escape message) "'")]
    (js-exec cmd)))

(defn windows-growl-notify! [title message]
  (log-info "trying to notify windows" title "-" message)
  (let [cmd (str (aget js/global "__dirname")
                 path-separator
                 "bin"
                 path-separator
                 "growlnotify.exe "
                 ;;"/a:Cuttle " ;; this is not working until we register the
                                ;; application first?
                 "/t:\"" (windows-shell-escape title) "\" "
                 "/i:\"" (windows-shell-escape cuttle-icon) "\" "
                 "\"" (windows-shell-escape message) "\"")]
    (js-exec cmd)))








;; NOTE: Sublime Text syntax highlighting chokes on the regex in these
;; functions, so I put them at the bottom so it doesn't mess up the rest of the
;; file

(defn- extract-target-from-start-msg [s]
  (-> s
    (replace #"^Compiling \"" "")
    (replace #"\".+$" "")))

(defn- parse-java-version
  [output]
  (when-let [m (re-find #"java version \"1\.(\d+)." output)]
    (int (second m))))
