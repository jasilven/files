(ns files.handlers-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [files.db :as db]
            [files.handlers :as h]))

(def test-ds (db/get-datasource (read-string (slurp "config_test.edn"))))
(def test-document {:file_name "test/testfile.pdf"
                    :mime_type "application/pdf"
                    :file_data (h/encode-b64 (h/file->bytes "test/testfile.pdf"))})

(def test-document2 {:file_name "test/testfile.jpg"
                     :mime_type "image/jpg"
                     :file_data (h/encode-b64 (h/file->bytes "test/testfile.jpg"))})

(defn response->document
  "parse document from http response to edn"
  [response]
  (json/read-str (:body response) :key-fn keyword))

(defn test-fixture-each [f]
  (db/create-files-table test-ds)
  (f)
  (db/drop-files-table test-ds))

(defn test-fixture-once [f]
  (f)
  (.close test-ds))

(t/use-fixtures :each test-fixture-each)
(t/use-fixtures :once test-fixture-once)

(t/deftest get-empty
  (t/testing "try to get stuff from empty db"
    (t/is (= [] (h/get-documents test-ds 10)))
    (t/is (= {:status 200
              :body "[]"
              :headers {"Content-Type" "application/json"}}
             (h/get-documents-json test-ds 10)))))

(t/deftest create-test
  (t/testing "create document"
    (let [body (.getBytes (json/write-str test-document))
          response (h/create-document test-ds {:body body})
          document (response->document response)]
      (t/is (= 200 (:status response)))
      (t/is (= {"Content-Type" "application/json"} (:headers response)))
      (t/is (= false (empty? (:id document)))))))

(t/deftest get-document
  (t/testing "create and then get single file with data"
    (let [body (.getBytes (json/write-str test-document))
          create-resp (h/create-document test-ds {:body body})
          create-body (response->document create-resp)
          id (:id create-body)
          get-resp (h/get-document test-ds id true)
          get-nonexist (h/get-document test-ds (db/uuid) true)
          get-invalid (h/get-document test-ds "invalid-id" true)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp)))
      (t/is (= 404 (:status get-nonexist)))
      (t/is (= 404 (:status get-invalid)))
      (t/is (= "{\"result\":" (subs (:body get-invalid) 0 10))))))

(t/deftest get-documents
  (t/testing "create and then get files"
    (let [body (.getBytes (json/write-str test-document))
          _ (h/create-document test-ds {:body body})
          response (h/get-documents-json test-ds 10)
          get-body (response->document response)]
      (t/is (= 200 (:status response)))
      (t/is (= 1 (count get-body)))
      (t/is (= false (empty? (:id (first get-body)))))
      (t/is (= false (empty? (:created (first get-body)))))
      (t/is (= (:file_name test-document) (:file_name (first get-body))))
      (t/is (= "application/pdf" (:mime_type (first get-body)))))))

(t/deftest get-info
  (t/testing "create and then get file info as json"
    (let [body (.getBytes (json/write-str test-document))
          create-resp (h/create-document test-ds {:body body})
          body        (response->document create-resp)
          id          (:id body)
          info-resp   (h/get-document test-ds id false)
          info-body   (response->document info-resp)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status info-resp)))
      (t/is (= (:file_name test-document) (:file_name info-body)))
      (t/is (= "application/pdf" (:mime_type info-body)))
      (t/is (= false (empty? (:id info-body))))
      (t/is (= false (empty? (:created info-body)))))))

(t/deftest delete-test
  (t/testing "create and then delete single file"
    (let [body (.getBytes (json/write-str test-document))
          create-resp (h/create-document test-ds {:body body})
          body (response->document create-resp)
          id (:id body)
          delete-resp1 (h/delete-document test-ds id)
          delete-resp2 (h/delete-document test-ds id)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status delete-resp1)))
      (t/is (= 200 (:status delete-resp2)))
      (t/is (= "{\"result\":{\"update-count\":1}}" (:body delete-resp1))) ;; number of rows deleted first time
      (t/is (= "{\"result\":{\"update-count\":0}}" (:body delete-resp2)))))) ;; number of rows deleted after first delete

(t/deftest update-test
  (t/testing "create and then update single file"
    (let [body1 (.getBytes (json/write-str test-document))
          response1 (h/create-document test-ds {:body body1})
          id1 (:id (response->document response1))
          original (response->document (h/get-document test-ds id1 true))
          body2 (.getBytes (json/write-str test-document2))
          response2 (h/update-document test-ds id1 {:body body2})
          updated (response->document (h/get-document test-ds (:id original) true))]
      (t/is (= true (contains? (json/read-str (:body response1)) "id")))
      (t/is (= "{\"result\":{\"update-count\":1}}" (:body response2)))
      (t/is (= 200 (:status response1)))
      (t/is (= 200 (:status response2)))
      (t/is (= (:id original) (:id updated)))
      (t/is (= (:created original) (:created updated)))
      (t/is (some? (:updated updated)))
      (t/is (not= (:updated original) (:updated updated)))
      (t/is (not= (:mime_type original) (:mime_type updated)))
      (t/is (not= (:file_name original) (:file_name updated)))
      (t/is (not= (count (:file_data original)) (count (:file_data updated)))))))
