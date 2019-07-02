(defproject files "0.1.0"
  :description "files"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring "1.7.0"]
                 [org.clojure/core.async "0.4.500"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [compojure "1.6.1"]
                 [org.clojure/data.json "0.2.6"]
                 [hiccup "1.0.5"]
                 [org.clojure/tools.logging "0.4.1"]]
  :main files.core
  :profiles {:uberjar {:aot :all}
             :dev {ˆ:skip-aot :all}})
