(ns cuttle.config
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [cuttle.util :refer [file-exists? js-log log]]))

;;------------------------------------------------------------------------------
;; Config File
;; NOTE: this is mostly for development purposes
;;------------------------------------------------------------------------------

(def config-file-path (str js/__dirname "/config.json"))

(def default-config {:log-compiler-output false })

(def config
  (if (file-exists? config-file-path)
    (-> (js/require config-file-path) js->clj keywordize-keys)
    default-config))

;;------------------------------------------------------------------------------
;; Path to App Data (OS-specific)
;;------------------------------------------------------------------------------

(def app-data-path
  "Path to the OS-specific config directory. Gets set! on app load."
  nil)
