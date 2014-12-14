(defproject add-lein-profile "0.1.0"
  :description "Adds a given lein profile to global user profiles config"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [rewrite-clj "0.3.12"]
                 [leiningen-core "2.5.0"]
                 [me.raynes/fs "1.4.6"]]
  :main ^:skip-aot add-lein-profile.core
  :profiles {:uberjar {:aot :all}})

