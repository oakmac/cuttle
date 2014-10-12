(defproject cljs-gui "0.1.0"
  :source-paths ["src-clj"]

  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [org.clojure/clojurescript "0.0-2234"]
    [hiccups "0.3.0"]
    [quiescent "0.1.4"]
    [sablono "0.2.22"]]

  :plugins [
    [lein-cljsbuild "1.0.3"]]

  :cljsbuild {
    :builds {

      :app-main {
        :source-paths ["src-cljs"]
        :compiler {
          :optimizations :whitespace
          :output-to "cljsbuild-ui/js/main.js"}}

      :public-main {
        :source-paths ["src-cljs"]
        :compiler {
          :optimizations :whitespace
          :output-to "public/js/main.js"}}

      :app-main-min {
        :source-paths ["src-cljs"]
        :compiler {
          :externs ["externs/react-0.11.0.js"]
          :output-to "cljsbuild-ui/js/main.min.js"
          :optimizations :advanced
          :pretty-print false }}

      :public-main-min {
        :source-paths ["src-cljs"]
        :compiler {
          :externs ["externs/react-0.11.0.js"]
          :output-to "public/js/main.min.js"
          :optimizations :advanced
          :pretty-print false }}}})