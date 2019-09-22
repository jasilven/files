(ns files.handlers-test
  (:require [clojure.test :as t]
            [files.json :as json]
            [files.db :as db]
            [files.test-data :refer :all]
            [files.handlers :as h]))

(defn test-fixture-each [f]
  (db/setup-datasource test-db-spec)
  (db/create-files-table)
  (f)
  (db/drop-files-table)
  (db/drop-datasource))

(t/use-fixtures :each test-fixture-each)

(t/deftest get-empty
  (t/testing "try to get stuff from empty db"
    (t/is (= [] (h/get-documents test-identity 10)))
    (t/is (= {:status 200
              :body "[]"
              :headers {"Content-Type" "application/json"}}
             (h/get-documents-json test-identity 10)))))

(t/deftest create-test
  (t/testing "create document"
    (let [body (.getBytes (json/clj->json test-document))
          response (h/create-document (merge {:body body} test-identity))
          document (json/json->clj (:body response))]
      (t/is (= 200 (:status response)))
      (t/is (= {"Content-Type" "application/json"} (:headers response)))
      (t/is (some? (:id document)))
      (t/is (uuid? (java.util.UUID/fromString (:id document))))))
  (t/testing "create without required field"
    (let [test-doc (dissoc test-document :mimetype)
          body (.getBytes (json/clj->json test-doc))
          response (h/create-document {{:body body} test-identity})]
      (t/is (= 400 (:status response)))
      (t/is (contains? (json/json->clj (:body response)) :result))))
  (t/testing "create with empty required field"
    (let [test-doc (assoc test-document :filedata "")
          body (.getBytes (json/clj->json test-doc))
          response (h/create-document (merge {:body body} test-identity))]
      (t/is (= 400 (:status response)))
      (t/is (contains? (json/json->clj (:body response)) :result))))
  (t/testing "create without filedata"
    (let [test-doc (assoc test-document :filedata nil)
          body (.getBytes (json/clj->json test-doc))
          response (h/create-document (merge {:body body} test-identity))]
      (t/is (= 400 (:status response)))
      (t/is (contains? (json/json->clj (:body response)) :result)))))

(t/deftest create-invalid-document
  (t/testing "create with incorrect json in metadata"
    (let [test-body "{\"filename\":\"testfile.pdf\",\"mimetype\":\"Y\",\"filedata\":\"Z\",\"metadata\":{\"zs}\"}"
          response (h/create-document (merge {:body (.getBytes test-body)} test-identity))]
      (t/is (= 400 (:status response)))
      (t/is (contains? (json/json->clj (:body response)) :result))))
  (t/testing "create with empty metadata"
    (let [test-body "{\"filename\":\"testfile.pdf\",\"mimetype\":\"Y\",\"filedata\":\"Z\",\"metadata\":\"}"
          response (h/create-document (merge {:body (.getBytes test-body)} test-identity))]
      (t/is (= 400 (:status response)))))
  (t/testing "create with invalid metadata"
    (let [test-body "{\"filename\":\"testfile.pdf\",\"mimetype\":\"Y\",\"filedata\":\"Z\",\"metadata\":invalid\"}"
          response (h/create-document (merge {:body (.getBytes test-body)} test-identity))]
      (t/is (= 400 (:status response)))))
  (t/testing "create document with invalid metadata"
    (let [doc (assoc test-document :metadata "invalid")
          body (.getBytes (json/clj->json doc))
          response (h/create-document (merge {:body body} test-identity))]
      (t/is (= 400 (:status response)))
      (t/is (= {"Content-Type" "application/json"} (:headers response))))))

(t/deftest get-document
  (t/testing "create and then get single file with data"
    (let [body (.getBytes (json/clj->json test-document))
          create-resp (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body create-resp)))
          get-resp-binary (h/get-document test-identity id)
          get-resp-nobinary (h/get-document test-identity id "no")
          get-nonexist (h/get-document test-identity (db/uuid))
          get-invalid (h/get-document test-identity "invalid-id")]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp-binary)))
      (t/is (= 200 (:status get-resp-nobinary)))
      (t/is (= 404 (:status get-nonexist)))
      (t/is (= 404 (:status get-invalid)))
      (t/is (contains? (json/json->clj (:body get-nonexist)) :result))
      (t/is (contains? (json/json->clj (:body get-invalid)) :result)))))

(t/deftest download-document
  (t/testing "create and then download document as file"
    (let [body (.getBytes (json/clj->json test-document))
          create-resp (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body create-resp)))
          download-resp (h/download (merge {:body body} test-identity) id)
          get-nonexist (h/download test-identity (db/uuid))
          get-invalid (h/download test-identity "invalid-id")]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status download-resp)))
      (t/is (some? (:body download-resp)))
      (t/is (= 404 (:status get-nonexist)))
      (t/is (= 404 (:status get-invalid))))))

(t/deftest get-document-schema
  (t/testing "create/get and check returned document schema"
    (let [body (.getBytes (json/clj->json test-document))
          create-resp (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body create-resp)))
          get-resp (h/get-document test-identity id "no")
          document (json/json->clj (:body get-resp))]
      (t/is (= 200 (:status get-resp)))
      (t/is (contains? document :id))
      (t/is (contains? document :created))
      (t/is (contains? document :updated))
      (t/is (contains? document :closed))
      (t/is (contains? document :filename))
      (t/is (contains? document :category))
      (t/is (contains? document :metadata))
      (t/is (contains? document :filesize))
      (t/is (contains? document :mimetype)))))

(t/deftest get-documents
  (t/testing "create and then get files"
    (let [body (.getBytes (json/clj->json test-document))
          _ (h/create-document (merge {:body body} test-identity))
          response (h/get-documents-json test-identity 10)
          get-body (json/json->clj (:body response))]
      (t/is (= 200 (:status response)))
      (t/is (= 1 (count get-body)))
      (t/is (= false (empty? (:id (first get-body)))))
      (t/is (= false (empty? (:created (first get-body)))))
      (t/is (= (:filename test-document) (:filename (first get-body))))
      (t/is (= (:mimetype test-document) (:mimetype (first get-body)))))))

(t/deftest get-info
  (t/testing "create and then get file info as json"
    (let [body (.getBytes (json/clj->json test-document))
          create-resp (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body create-resp)))
          get-resp (h/get-document test-identity id "no")
          document (json/json->clj (:body get-resp))]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp)))
      (t/is (= (:filename test-document) (:filename document)))
      (t/is (= (:mimetype test-document) (:mimetype document)))
      (t/is (not (empty? (:id document))))
      (t/is (not (empty? (:created document)))))))

(t/deftest close-test
  (t/testing "create and then close single file"
    (let [body (.getBytes (json/clj->json test-document))
          create-resp (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body create-resp)))
          close-resp (h/close-document test-identity id)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status close-resp)))
      (t/is (= {:result "success"} (json/json->clj (:body close-resp))))))
  (t/testing "try to update non-existing document"
    (let [resp (h/close-document "non-existing-id" test-identity)]
      (t/is (= 400 (:status resp)))
      (t/is (contains? (json/json->clj (:body resp)) :result)))))

(t/deftest update-test
  (t/testing "create and then update single file"
    (let [body1 (.getBytes (json/clj->json test-document))
          response1 (h/create-document (merge {:body body1} test-identity))
          id1 (:id (json/json->clj (:body response1)))
          original (json/json->clj (:body (h/get-document test-identity id1)))
          body2 (.getBytes (json/clj->json test-document2))
          response2 (h/update-document (merge {:body body2} test-identity) id1)
          updated (json/json->clj (:body (h/get-document test-identity (:id original))))
          _ (println (keys updated))]
      (t/is (contains? (json/json->clj (:body response1)) :id))
      (t/is (= {:result "success"} (json/json->clj (:body response2))))
      (t/is (= 200 (:status response1)))
      (t/is (= 200 (:status response2)))
      (t/is (= (:id original) (:id updated)))
      (t/is (= (:created original) (:created updated)))
      (t/is (some? (:updated updated)))
      (t/is (not= (:updated original) (:updated updated)))
      (t/is (not= (:mimetype original) (:mimetype updated)))
      (t/is (not= (:filename original) (:filename updated)))
      #_(t/is (not= (.length (:filedata original)) (.length (:filedata updated))))))
  (t/testing "try to update non-existing document"
    (let [body (.getBytes (json/clj->json test-document))
          resp (h/update-document (merge {:body body} test-identity) "non-existing-id" )]
      (t/is (= 400 (:status resp)))
      (t/is (contains? (json/json->clj (:body resp)) :result))))
  (t/testing "try to update non-valid document"
    (let [body (.getBytes (json/clj->json test-document))
          response (h/create-document (merge {:body body} test-identity))
          id (:id (json/json->clj (:body response)))
          doc (assoc test-document :metadata "invalid")
          body2 (.getBytes (json/clj->json doc))
          resp (h/update-document id (merge {:body body2} test-identity))]
      (t/is (= 400 (:status resp)))
      (t/is (contains? (json/json->clj (:body resp)) :result)))))
