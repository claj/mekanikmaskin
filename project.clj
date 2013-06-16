(defproject mekanikmaskin "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [com.datomic/datomic-free "0.8.4007" 
                  :exclusions [org.slf4j/slf4j-nop
                               org.slf4j/slf4j-log4j12]]] 
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})
