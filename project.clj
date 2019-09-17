(defproject files "0.2"
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :project]}
  :description "files API"
  :main files.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
