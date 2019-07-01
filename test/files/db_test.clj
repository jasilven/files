(ns files.db-test
  (:require [files.db :as db]
            [files.handlers :as h]
            [clojure.test :as t]))

(def test-db (clojure.edn/read-string (slurp "resources/test_db.edn")))

(def testfile "resources/testfile.pdf")

(defn test-fixture [f]
  (db/create-files-table test-db)
  (f)
  (db/drop-files-table test-db))

(t/use-fixtures :each test-fixture)

(t/deftest connection
  (t/is (= true (db/db-connection? test-db))))

(t/deftest files-exists
  (t/is (= true (db/files-exists? test-db))))

(t/deftest create-file
  (let [bytes (h/file->bytes testfile)
        fname "create-file testfile"
        mime  "application/pdf"
        result (first (db/create-file test-db
                                      fname
                                      mime
                                      bytes))]
    (t/is (= fname (:file_name result)))
    (t/is (= mime (:mime_type result)))
    (t/is (= true (uuid? (java.util.UUID/fromString (:id result)))))))

(t/deftest get-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (first (db/create-file test-db
                                               "get-file testfile"
                                               "application/pdf"
                                               bytes))
                        :file_data)
        getted (dissoc (first (db/get-file test-db (:id created))) :file_data)]
    (t/is (= getted created))))

(t/deftest get-files
  (let [cnt 100
        bytes (h/file->bytes testfile)]
    (dotimes [n cnt]
      (db/create-file test-db
                      (str "get-files testfile" n)
                      "application/pdf"
                      bytes))
    (t/is (= cnt (count (db/get-files test-db (+ 10 cnt)))))))


(t/deftest delete-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (first (db/create-file test-db
                                               "delete-file testfile"
                                               "application/pdf"
                                               bytes))
                        :file_data)
        deleted (db/delete-file test-db (:id created))]
    (t/is (= 1 (first deleted)))
    (t/is (= 0 (count (db/get-files test-db 2))))))
