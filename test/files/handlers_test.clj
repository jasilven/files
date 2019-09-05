(ns files.handlers-test
  (:require [cheshire.core :as json]
            [clojure.test :as t]
            [files.db :as db]
            [files.b64 :as b64]
            [files.handlers :as h]))

(def test-ds (db/get-datasource (read-string (slurp "config_test.edn"))))
(def test-document {:file_name "test/testfile.pdf"
                    :mime_type "application/pdf"
                    :file_data (b64/encode-file "test/testfile.pdf")})

(def test-document2 {:file_name "test/testfile.jpg"
                     :mime_type "image/jpg"
                     :file_data (b64/encode-file "test/testfile.jpg")})

(defn response->document
  "parse document from http response to document edn"
  [response]
  (json/parse-string (:body response) true))

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
    (let [body (.getBytes (json/generate-string test-document))
          response (h/create-document test-ds {:body body})
          document (response->document response)]
      (t/is (= 200 (:status response)))
      (t/is (= {"Content-Type" "application/json"} (:headers response)))
      (t/is (= false (empty? (:id document))))))
  (t/testing "create without required field"
    (let [test-doc (dissoc test-document :mime_type)
          body (.getBytes (json/generate-string test-doc))
          response (h/create-document test-ds {:body body})]
      (t/is (= 400 (:status response)))
      (t/is (= "{\"result\":" (subs (:body response) 0 10)))))
  (t/testing "create with empty required field"
    (let [test-doc (assoc test-document :file_data "")
          body (.getBytes (json/generate-string test-doc))
          response (h/create-document test-ds {:body body})]
      (t/is (= 400 (:status response)))
      (t/is (= "{\"result\":" (subs (:body response) 0 10)))))
  (t/testing "create without file_data"
    (let [test-doc (assoc test-document :file_data nil)
          body (.getBytes (json/generate-string test-doc))
          response (h/create-document test-ds {:body body})]
      (t/is (= 400 (:status response)))
      (t/is (= "{\"result\":" (subs (:body response) 0 10))))))

(t/deftest create-test-illegal-json
  (t/testing "create with incorrect json in metadata"
    (let [test-body "{\"file_name\":\"testfile.pdf\",\"mime_type\":\"Y\",\"file_data\":\"Z\",\"metadata\":{\"zs}\"}"
          response (h/create-document test-ds {:body (.getBytes test-body)})]
      (t/is (= 400 (:status response)))
      (t/is (= "{\"result\":" (subs (:body response) 0 10))))))

(t/deftest get-document
  (t/testing "create and then get single file with data"
    (let [body (.getBytes (json/generate-string test-document))
          create-resp (h/create-document test-ds {:body body})
          id (:id (response->document create-resp))
          get-resp (h/get-document test-ds id true)
          get-nonexist (h/get-document test-ds (db/uuid) true)
          get-invalid (h/get-document test-ds "invalid-id" true)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp)))
      (t/is (= 400 (:status get-nonexist)))
      (t/is (= 400 (:status get-invalid)))
      (t/is (= "{\"result\":" (subs (:body get-nonexist) 0 10)))
      (t/is (= "{\"result\":" (subs (:body get-invalid) 0 10))))))

(t/deftest get-documents
  (t/testing "create and then get files"
    (let [body (.getBytes (json/generate-string test-document))
          _ (h/create-document test-ds {:body body})
          response (h/get-documents-json test-ds 10)
          get-body (response->document response)]
      (t/is (= 200 (:status response)))
      (t/is (= 1 (count get-body)))
      (t/is (= false (empty? (:id (first get-body)))))
      (t/is (= false (empty? (:created (first get-body)))))
      (t/is (= (:file_name test-document) (:file_name (first get-body))))
      (t/is (= (:mime_type test-document) (:mime_type (first get-body)))))))

(t/deftest get-info
  (t/testing "create and then get file info as json"
    (let [body (.getBytes (json/generate-string test-document))
          create-resp (h/create-document test-ds {:body body})
          id (:id (response->document create-resp))
          get-resp (h/get-document test-ds id false)
          document (response->document get-resp)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp)))
      (t/is (= (:file_name test-document) (:file_name document)))
      (t/is (= (:mime_type test-document) (:mime_type document)))
      (t/is (not (empty? (:id document))))
      (t/is (not (empty? (:created document)))))))

(t/deftest close-test
  (t/testing "create and then close single file"
    (let [body (.getBytes (json/generate-string test-document))
          create-resp (h/create-document test-ds {:body body})
          id (:id (response->document create-resp))
          close-resp (h/close-document test-ds id)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status close-resp)))
      (t/is (= "{\"result\":\"success\"}" (:body close-resp)))))
  (t/testing "try to update non-existing document"
    (let [resp (h/close-document test-ds "non-existing-id")]
      (t/is (= 400 (:status resp)))
      (t/is (= "{\"result\":" (subs (:body resp) 0 10))))))

(t/deftest update-test
  (t/testing "create and then update single file"
    (let [body1 (.getBytes (json/generate-string test-document))
          response1 (h/create-document test-ds {:body body1})
          id1 (:id (response->document response1))
          original (response->document (h/get-document test-ds id1 true))
          body2 (.getBytes (json/generate-string test-document2))
          response2 (h/update-document test-ds id1 {:body body2})
          updated (response->document (h/get-document test-ds (:id original) true))]
      (t/is (= true (contains? (json/parse-string (:body response1)) "id")))
      (t/is (= "{\"result\":\"success\"}" (:body response2)))
      (t/is (= 200 (:status response1)))
      (t/is (= 200 (:status response2)))
      (t/is (= (:id original) (:id updated)))
      (t/is (= (:created original) (:created updated)))
      (t/is (some? (:updated updated)))
      (t/is (not= (:updated original) (:updated updated)))
      (t/is (not= (:mime_type original) (:mime_type updated)))
      (t/is (not= (:file_name original) (:file_name updated)))
      (t/is (not= (count (:file_data original)) (count (:file_data updated))))))
  (t/testing "try to update non-existing document"
    (let [resp (h/update-document test-ds "non-existing-id"
                                  {:body (.getBytes (json/generate-string test-document))})]
      (t/is (= 400 (:status resp)))
      (t/is (= "{\"result\":" (subs (:body resp) 0 10))))))
