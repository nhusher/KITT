(defproject kitt "0.1.0-SNAPSHOT"
  :description "Turbo boost"
  :url "http://github.com/nhusher/KITT"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [com.stuartsierra/component "0.2.3"]
                 [ring/ring-servlet "1.3.2"]
                 [stylefruits/gniazdo "0.4.0"]
								 [clj-http "1.0.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "4.1.1"]
                 [cheshire "5.4.0"]
                 [environ "1.0.0"]
                 [clojail "1.0.6"]]

  :main ^:skip-aot kitt.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})