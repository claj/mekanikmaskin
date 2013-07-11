(defproject mekanikmaskin "0.1.0-SNAPSHOT"
  :description "A way to learn basic university mechanics courses"
  :url "https://github.com/claj/mekanikmaskin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [io.pedestal/pedestal.service "0.1.10-SNAPSHOT"]
                 [io.pedestal/pedestal.jetty "0.1.10-SNAPSHOT"]
                 [ns-tracker "0.2.1"]
                 [ch.qos.logback/logback-classic "1.0.13" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.2"]
                 [org.slf4j/jcl-over-slf4j "1.7.2"]
                 [org.slf4j/log4j-over-slf4j "1.7.2"]
                 
                 [com.datomic/datomic-free "0.8.4007" 
                  :exclusions [org.slf4j/slf4j-nop
                               org.slf4j/slf4j-log4j12]]
                 ;;credential encryption
                 [org.jasypt/jasypt "1.9.0"]
] 
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :source-paths ["dev"]}}
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "dev"]}
  :main ^{:skip-aot true} mekanikmaskin.server)
