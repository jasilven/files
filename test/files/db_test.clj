(ns files.db-test
  (:require [files.db :as db]
            [files.handlers :as h]
            [clojure.test :as t]))

(def test-config)
(def test-ds (db/get-datasource (read-string (slurp "config_test.edn"))))

(def testfile "resources/testfile.pdf")

(defn test-fixture-once [f]
  (f)
  (.close test-ds))

(defn test-fixture-each [f]
  (db/create-files-table test-ds)
  (f)
  (db/drop-files-table test-ds))

(t/use-fixtures :once test-fixture-once)
(t/use-fixtures :each test-fixture-each)

(t/deftest connection
  (t/is (= true (db/db-connection? test-ds)))
  (t/is (= false (db/db-connection? "non-existing-db"))))

(t/deftest files-exists
  (t/is (= true (db/files-exists? test-ds)))
  (t/is (= false (db/files-exists? "non-existing-db"))))

(t/deftest create-file
  (let [bytes (h/file->bytes testfile)
        fname "create-file testfile"
        mime  "application/pdf"
        result (db/create-file test-ds fname mime bytes)
        id (:files/id result)]
    (t/is (= true
             (uuid? (java.util.UUID/fromString id))
             (db/valid-id? id)))))

(t/deftest get-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (db/create-file test-ds
                                        "get-file testfile"
                                        "application/pdf"
                                        bytes)
                        :file_data)
        id (:files/id created)
        getted (db/get-file test-ds id true)
        getted-no-bin (db/get-file test-ds id false)]
    (t/is (= (:files/id created) (:files/id getted)))
    (t/is (= (:files/file_type created) (:files/file_type getted)))
    (t/is (thrown? Exception (db/get-file "non-existing-db" id false)))
    (t/is (thrown? Exception (db/get-file test-ds "non-existing-id" false)))
    (t/is (= true (contains? getted :files/file_data)))
    (t/is (= false (contains? getted-no-bin :files/file_data)))))

(t/deftest get-files
  (let [cnt 20
        bytes (h/file->bytes testfile)]
    (dotimes [n cnt]
      (db/create-file test-ds
                      (str "get-files testfile" n)
                      "application/pdf"
                      bytes))
    (t/is (= cnt (count (db/get-files test-ds (+ 10 cnt)))))
    (t/is (= 1 (count (db/get-files test-ds 1))))
    (t/is (thrown? Exception (db/get-files test-ds 0)))
    (t/is (thrown? Exception (db/get-files "non-existing-db")))
    (t/is (thrown? Exception (db/get-files test-ds -1)))))

(t/deftest delete-file
  (let [bytes (h/file->bytes testfile)
        created (dissoc (db/create-file test-ds
                                        "delete-file testfile"
                                        "application/pdf"
                                        bytes)
                        :file_data)
        id (:files/id created)
        deleted (db/delete-file test-ds id)]
    (t/is (thrown? Exception (db/delete-file test-ds "non-existing-id")))
    (t/is (thrown? Exception (db/delete-file "non-existing-db" id)))
    (t/is (= #:next.jdbc{:update-count 1} deleted))
    (t/is (= 0 (count (db/get-files test-ds 2))))))
