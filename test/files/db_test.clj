(ns files.db-test
  (:require [files.db :as db]
            [files.handlers :as h]
            [clojure.test :as t]))

(def test-db (:db-spec (clojure.edn/read-string (slurp "config_test.edn"))))

(def testfile "resources/testfile.pdf")

(defn test-fixture [f]
  (db/create-files-table test-db)
  (f)
  (db/drop-files-table test-db))

(t/use-fixtures :each test-fixture)

(t/deftest connection
  (t/is (= true (db/db-connection? test-db)))
  (t/is (= false (db/db-connection? "non-existing-db"))))

(t/deftest files-exists
  (t/is (= true (db/files-exists? test-db)))
  (t/is (= false (db/files-exists? "non-existing-db"))))
(t/deftest create-file

  (let [bytes (h/file->bytes testfile)
        fname "create-file testfile"
        mime  "application/pdf"
        result (db/create-file test-db
                               fname
                               mime
                               bytes)
        id (:id result)]
    (t/is (= fname (:file_name result)))
    (t/is (= mime (:mime_type result)))
    (t/is (= true
             (uuid? (java.util.UUID/fromString id))
             (db/valid-id? id)))))

(t/deftest get-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (db/create-file test-db
                                        "get-file testfile"
                                        "application/pdf"
                                        bytes)
                        :file_data)
        id (:id created)
        getted (db/get-file test-db id false)]
    (t/is (= getted created))
    (t/is (thrown? Exception (db/get-file "non-existing-db" id false)))
    (t/is (thrown? Exception (db/get-file test-db "non-existing-id" false)))))

(t/deftest get-files
  (let [cnt 20
        bytes (h/file->bytes testfile)]
    (dotimes [n cnt]
      (db/create-file test-db
                      (str "get-files testfile" n)
                      "application/pdf"
                      bytes))
    (t/is (= cnt (count (db/get-files test-db (+ 10 cnt)))))
    (t/is (= 1 (count (db/get-files test-db 1))))
    (t/is (thrown? Exception (db/get-files test-db 0)))
    (t/is (thrown? Exception (db/get-files "non-existing-db")))
    (t/is (thrown? Exception (db/get-files test-db -1)))))

(t/deftest delete-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (db/create-file test-db
                                        "delete-file testfile"
                                        "application/pdf"
                                        bytes)
                        :file_data)
        id (:id created)
        deleted (db/delete-file test-db id)]
    (t/is (thrown? Exception (db/delete-file test-db "non-existing-id")))
    (t/is (thrown? Exception (db/delete-file "non-existing-db" id)))
    (t/is (= 1 (first deleted)))
    (t/is (= 0 (count (db/get-files test-db 2))))))
