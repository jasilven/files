(ns files.db-test
  (:require [files.db :as db]
            [files.test-data :refer :all]
            [taoensso.timbre :as timbre]
            [clojure.test :as t]))

(defn test-fixture-each [f]
  (timbre/with-level (:level test-timbre)
    (db/setup-datasource test-db-spec)
    (db/create-files-table)
    (f)
    (db/drop-files-table)
    (db/drop-datasource)))

(t/use-fixtures :each test-fixture-each)

(t/deftest create-document
  (t/testing "creating document"
    (let [result (db/create-document test-document (:identity test-identity))]
      (t/is (not (nil? result)))
      (t/is (contains? result :id))
      (t/is (uuid? (java.util.UUID/fromString (:id result)))))))

(t/deftest get-document
  (t/testing "getting document with all data"
    (let [created (db/create-document test-document (:identity test-identity))
          result (db/get-document (:id created) true (:identity test-identity))]
      (t/is (not (nil? result)))
      (t/is (contains? result :created))
      (t/is (contains? result :filedata))
      (t/is (contains? result :filename))
      (t/is (contains? result :mimetype)))
    (t/testing "getting document without filedata"
      (let [created (db/create-document test-document (:identity test-identity))
            result (db/get-document (:id created) false (:identity test-identity))]
        (t/is (not (contains? result :filedata)))))
    (t/testing "getting non-existing document"
      (t/is (nil? (db/get-document "non-existing-id" false (:identity test-identity)))))))

(t/deftest update-document
  (t/testing "updating document"
    (let [created (db/create-document test-document (:identity test-identity))]
      (t/is (= true (db/update-document (:id created) test-document2 (:identity test-identity))))))
  (t/testing "updating non existing document"
    (t/is (thrown? Exception (db/update-document "non-existing-id" test-document2 (:identity test-identity))))))

(t/deftest close-document
  (t/testing "close document"
    (let [created (db/create-document test-document (:identity test-identity))]
      (t/is (= true (db/close-document (:id created) (:identity test-identity))))))
  (t/testing "close document twice"
    (let [created (db/create-document test-document (:identity test-identity))
          _ (db/close-document (:id created) (:identity test-identity))]
      (t/is (= false (db/close-document (:id created) (:identity test-identity))))))
  (t/testing "deleting non existing document"
    (t/is (thrown? Exception (db/close-document "non-existing-id" (:identity test-identity))))))

(t/deftest open-document
  (t/testing "open document"
    (let [created (db/create-document test-document (:identity test-identity))
          _ (db/close-document (:id created) (:identity test-identity))]
      (t/is (= true (db/open-document (:id created) (:identity test-identity))))))
  (t/testing "open document twice"
    (let [created (db/create-document test-document (:identity test-identity))
          _ (db/close-document (:id created) (:identity test-identity))
          _ (db/open-document (:id created) (:identity test-identity))]
      (t/is (= false (db/open-document (:id created) (:identity test-identity))))))
  (t/testing "deleting non existing document"
    (t/is (thrown? Exception (db/open-document "non-existing-id") (:identity test-identity)))))
