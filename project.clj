(defproject files "0.3.2"
  :plugins [[lein-tools-deps "0.4.5"] ]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :project]}
  :description "Files API"
  :main files.core
  :target-path "target/%s"
  :clean-targets ^{:protect false} ["resources/public/js/manifest.edn"
                                    "target"
                                    "resources/public/js/cljs-runtime"]
  :profiles {:uberjar {:aot :all}})
