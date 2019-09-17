(ns files.test-data
  (:require [files.b64 :as b64]))

(def test-db-spec (:db-spec (read-string (slurp "config_test.edn"))))
(def test-document {:filename "test/testfile.pdf"
                    :mimetype "application/pdf"
                    :filedata (b64/encode-file "test/testfile.pdf")})
(def test-document2 {:filename "test/testfile.jpg"
                     :mimetype "image/jpg"
                     :filedata (b64/encode-file "test/testfile.jpg")})
(def test-identity {:identity {:user "testuser" :origin "db-test"}})
