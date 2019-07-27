(ns files.b64
  (:import java.util.Base64)
  (:require [clojure.java.io :as io]))

(defn file->bytes
  "Open fname as file and return it's content bytes."
  [fname]
  (with-open [xin (io/input-stream (io/file fname))
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn encode-file
  "Encode file identified by fname to base64 string."
  [fname]
  (let [barray (file->bytes fname)]
    (String. (.encode (Base64/getEncoder) barray) "UTF-8")))

(defn decode
  "Decode base64 string."
  [s]
  (.decode (Base64/getDecoder) s))
