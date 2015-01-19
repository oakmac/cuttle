(defproject cuttle "1.0-RC1"
  :description "A user interface for the ClojureScript compiler."
  :url "https://github.com/oakmac/cuttle"
  :license {
    :name "MIT License"
    :url "https://github.com/oakmac/cuttle/blob/master/LICENSE.md"
    :distribution :repo}

  :source-paths ["src-clj"]

  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [org.clojure/clojurescript "0.0-2371"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [hiccups "0.3.0"]
    [quiescent "0.1.4"]
    [sablono "0.2.22"]]

  :plugins [
    [lein-cljsbuild "1.0.3"]]

  :cljsbuild {
    :builds {

      :main {
        :source-paths ["src-cljs"]
        :compiler {
          :optimizations :whitespace
          :output-to "app/js/main.js"}}

      :main-min {
        :source-paths ["src-cljs"]
        :compiler {
          :externs ["externs/react-0.11.0.js" "externs/misc.js"]
          :output-to "app/js/main.min.js"
          :optimizations :advanced
          :pretty-print false }}}})
