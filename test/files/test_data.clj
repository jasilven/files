(ns files.test-data
  (:require [files.b64 :as b64]))

(System/setProperties
 (doto (java.util.Properties. (System/getProperties))
   (.put "com.mchange.v2.log.MLog" "com.mchange.v2.log.FallbackMLog")
   (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL" "OFF")))

(def CONFIG (read-string (slurp "config_test.edn")))
(def test-timbre (:timbre CONFIG))
(def test-db-spec (:db-spec CONFIG))
(def test-document {:filename "test/testfile.pdf"
                    :mimetype "application/pdf"
                    :filedata (b64/encode-file "test/testfile.pdf")})
(def test-document2 {:filename "test/testfile.jpg"
                     :mimetype "image/jpg"
                     :filedata (b64/encode-file "test/testfile.jpg")})
(def test-identity {:identity {:user "testuser" :origin "db-test"}})
