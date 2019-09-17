(ns files.json
  (:require [cheshire.core :as json]))

(defn json->clj
  "Convert json to clojure data. Maps keys to clojure keywords."
  [js]
  (json/parse-string js keyword))

(defn clj->json
  "Convert clojure data to JSON."
  [data]
  (json/generate-string data true))
