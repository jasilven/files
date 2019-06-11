(defproject files "0.1.0"
  :description "files"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring "1.7.0"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [compojure "1.6.1"]
                 [org.clojure/data.json "0.2.6"]
                 [hiccup "1.0.5"]]
  :main files
  :aot [files])