(defproject mblogic-clj "0.1.0"
  :description "Clojure/ClojureScript port of MBLogic PLC compiler/interpreter"
  :url "https://github.com/brooksg44/mblogic-clj"
  :license {:name "GPL-3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :author "Gregory Brooks"

  ;; Clojure version
  :dependencies [[org.clojure/clojure "1.11.1"]
                 ;; Web framework
                 [ring/ring-core "1.9.6"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [compojure "1.7.0"]
                 ;; JSON encoding/decoding
                 [cheshire "5.11.0"]
                 ;; Logging
                 [com.taoensso/timbre "6.1.0"]
                 ;; Utilities
                 [org.clojure/core.async "1.6.673"]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]
                                  [org.clojure/clojurescript "1.10.866"]]
                   :plugins [[lein-cljsbuild "1.1.8"]]}
             :test {:resource-paths ["resources" "test-resources"]}
             :uberjar {:aot :all
                       :main mblogic-clj.core}}

  ;; Main namespace
  :main mblogic-clj.core

  ;; Source paths
  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]

  ;; ClojureScript build
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/js/mblogic.js"
                                   :output-dir "resources/js/out"
                                   :source-map true
                                   :optimizations :none
                                   :pretty-print true}}
                       {:id "release"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/js/mblogic.js"
                                   :output-dir "resources/js/out-release"
                                   :optimizations :advanced
                                   :pretty-print false}}]}

  ;; Compilation
  :target-path "target/%s"
  :compile-path "target/classes"

  ;; Testing
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)}

  ;; REPL options
  :repl-options {:init-ns mblogic-clj.core
                 :timeout 120000})
