(defproject crdtdemo "0.1.0-SNAPSHOT"
  :description "Demoing CRDTs in browser"
  :url "http://example.com/FIXME"

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/core.match "0.2.1"]
                 [om "0.6.2"]]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :source-paths ["src"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                :output-to "app.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                :output-to "app.js"
                :optimizations :advanced
                :elide-asserts true
                :pretty-print false
                :output-wrapper false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]}}]})
