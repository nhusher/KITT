(defproject kitt "0.1.0-SNAPSHOT"
  :description "Turbo boost"
  :url "http://github.com/nhusher/KITT"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.eclipse.jetty/jetty-http "9.3.0.M1"]
                 [org.eclipse.jetty/jetty-server "9.3.0.M1"]
                 [ring "1.4.0" :exclusions [org.eclipse.jetty/jetty-http
                                            org.eclipse.jetty/jetty-server]]
                 [com.stuartsierra/component "0.2.3"]
                 [stylefruits/gniazdo "0.4.0"]
								 [clj-http "1.0.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "4.1.1"]
                 [cheshire "5.4.0"]
                 [environ "1.0.0"]
                 [clojail "1.0.6"]
                 [clj-time "0.8.0"]]

  :main ^:skip-aot kitt.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})