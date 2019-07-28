(ns files.db-test
  (:require [files.db :as db]
            [files.b64 :as b64]
            [clojure.test :as t]))

(def test-ds (db/get-datasource (read-string (slurp "config_test.edn"))))
(def test-document {:file_name "test/testfile.pdf"
                    :mime_type "application/pdf"
                    :file_data (b64/encode-file "test/testfile.pdf")})

(def test-document2 {:file_name "test/testfile.jpg"
                     :mime_type "image/jpg"
                     :file_data (b64/encode-file "test/testfile.jpg")})

(defn test-fixture-each [f]
  (db/create-files-table test-ds)
  (f)
  (db/drop-files-table test-ds))

(defn test-fixture-once [f]
  (f)
  (.close test-ds))

(t/use-fixtures :each test-fixture-each)
(t/use-fixtures :once test-fixture-once)

(t/deftest create-document
  (t/testing "creating document"
    (let [result (db/create-document test-ds test-document)]
      (t/is (not (nil? result)))
      (t/is (contains? result :id)))))

(t/deftest get-document
  (t/testing "getting document with all data"
    (let [created (db/create-document test-ds test-document)
          result (db/get-document test-ds (:id created) true)]
      (t/is (not (nil? result)))
      (t/is (contains? result :created))
      (t/is (contains? result :file_data))
      (t/is (contains? result :file_name))
      (t/is (contains? result :mime_type)))
    (t/testing "getting document without file_data"
      (let [created (db/create-document test-ds test-document)
            result (db/get-document test-ds (:id created) false)]
        (t/is (not (contains? result :file_data)))))
    (t/testing "getting non-existing document"
      (t/is (nil? (db/get-document test-ds "non-existing-id" false))))))

(t/deftest update-document
  (t/testing "updating document"
    (let [created (db/create-document test-ds test-document)]
      (t/is (= true (db/update-document test-ds (:id created) test-document2)))))
  (t/testing "updating non existing document"
    (t/is (thrown? Exception (db/update-document test-ds "non-existing-id" test-document2)))))

(t/deftest close-document
  (t/testing "close document"
    (let [created (db/create-document test-ds test-document)]
      (t/is (= true (db/close-document test-ds (:id created))))))
  (t/testing "close document twice"
    (let [created (db/create-document test-ds test-document)
          _ (db/close-document test-ds (:id created))]
      (t/is (= false (db/close-document test-ds (:id created))))))
  (t/testing "deleting non existing document"
    (t/is (thrown? Exception (db/close-document test-ds "non-existing-id")))))

(t/deftest open-document
  (t/testing "open document"
    (let [created (db/create-document test-ds test-document)
          _ (db/close-document test-ds (:id created))]
      (t/is (= true (db/open-document test-ds (:id created))))))
  (t/testing "open document twice"
    (let [created (db/create-document test-ds test-document)
          _ (db/close-document test-ds (:id created))
          _ (db/open-document test-ds (:id created))]
      (t/is (= false (db/open-document test-ds (:id created))))))
  (t/testing "deleting non existing document"
    (t/is (thrown? Exception (db/open-document test-ds "non-existing-id")))))
