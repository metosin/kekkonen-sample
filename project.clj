(defproject example "0.1.0-SNAPSHOT"
  :description "Sample project with Kekkonen"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [com.stuartsierra/component "0.3.0"]
                 [reloaded.repl "0.2.1"]
                 [metosin/palikka "0.3.0"]
                 [metosin/kekkonen "0.1.0"]]
  :profiles {:uberjar {:aot [example.main]
                       :main example.main
                       :uberjar-name "example.jar"}})
