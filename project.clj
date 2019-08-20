(defproject files "0.1"
  :description "files API"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.10.0"]
                 [cheshire "5.8.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [org.clojure/tools.logging "0.4.1"]
                 [compojure "1.6.1"]
                 [seancorfield/next.jdbc "1.0.1"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.3"]
                 [org.postgresql/postgresql "42.2.5"]
                 [ring "1.7.0"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [hiccup "1.0.5"]
                 [ring-basic-authentication "1.0.5"]]
  :main files.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
