(ns cljsbuild-ui.config
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [cljsbuild-ui.util :refer [file-exists? js-log log]]))

;;------------------------------------------------------------------------------
;; Load Config File
;; NOTE: this is mostly for dev purposes
;;------------------------------------------------------------------------------

(def config-file-path (str js/__dirname "/config.json"))

(def default-config {:log-compiler-output false })

(def config
  (if (file-exists? config-file-path)
    (-> (js/require config-file-path) js->clj keywordize-keys)
    default-config))
