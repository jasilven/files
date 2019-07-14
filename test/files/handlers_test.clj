(ns files.handlers-test
  (:require [clojure.data.json :as json]
            [clojure.test :as t]
            [files.db :as db]
            [files.handlers :as h]))

(def test-db (:db-spec (clojure.edn/read-string (slurp "config_test.edn"))))
(def test-file "resources/testfile.pdf")
(def test-params {:params {"file" {:filename test-file
                                   :content-type "application/pdf"
                                   :tempfile test-file}}})
(defn test-fixture [f]
  (db/create-files-table test-db)
  (f)
  (db/drop-files-table test-db))

(t/use-fixtures :each test-fixture)

(t/deftest get-files-empty
  (t/testing "get-files from empty db"
    (t/is (= [] (h/get-files test-db 10)))
    (t/is (= {:status 200
              :body "[]"
              :headers {"Content-Type" "application/json"}}
             (h/get-files-json test-db 10)))))

(t/deftest create-file
  (t/testing "create-file"
    (let [response (h/create-file test-db test-params)
          body (json/read-str (:body response) :key-fn keyword)]
      (t/is (= 200 (:status response)))
      (t/is (= {"Content-Type" "application/json"} (:headers response)))
      (t/is (= false (empty? (:id body))))
      (t/is (= false (empty? (:created body))))
      (t/is (= "application/pdf" (:mime_type body)))
      (t/is (= test-file (:file_name body))))))

(t/deftest create-and-get-file
  (t/testing "create and then get single file with data"
    (let [create-resp (h/create-file test-db test-params)
          create-body (json/read-str (:body create-resp) :key-fn keyword)
          id (:id create-body)
          get-resp (h/get-file test-db id)
          get-nonexist (h/get-file test-db (db/uuid))
          get-invalid (h/get-file test-db "invalid-id")]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status get-resp)))
      (t/is (= 404 (:status get-nonexist)))
      (t/is (= 400 (:status get-invalid)))
      (t/is (= "{\"result\":" (subs (:body get-invalid) 0 10)))
      (t/is (< 0 (.available (:body get-resp)))))))

(t/deftest create-and-get-files
  (t/testing "create and then get files"
    (let [_ (h/create-file test-db test-params)
          response (h/get-files-json test-db 10)
          get-body (json/read-str (:body response) :key-fn keyword)]
      (t/is (= 200 (:status response)))
      (t/is (= 1 (count get-body)))
      (t/is (= false (empty? (:id (first get-body)))))
      (t/is (= false (empty? (:created (first get-body)))))
      (t/is (= test-file (:file_name (first get-body))))
      (t/is (= "application/pdf" (:mime_type (first get-body)))))))

(t/deftest create-and-get-info
  (t/testing "create and then get file info as json"
    (let [create-resp (h/create-file test-db test-params)
          body (json/read-str (:body create-resp) :key-fn keyword)
          id (:id body)
          info-resp (h/get-file-info test-db id)
          info-body (json/read-str (:body info-resp) :key-fn keyword)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status info-resp)))
      (t/is (= test-file (:file_name info-body)))
      (t/is (= "application/pdf" (:mime_type info-body)))
      (t/is (= false (empty? (:id info-body))))
      (t/is (= false (empty? (:created info-body)))))))

(t/deftest create-and-delete
  (t/testing "create and then delete single file"
    (let [create-resp (h/create-file test-db test-params)
          body (json/read-str (:body create-resp) :key-fn keyword)
          id (:id body)
          delete-resp1 (h/delete-file test-db id)
          delete-resp2 (h/delete-file test-db id)]
      (t/is (= 200 (:status create-resp)))
      (t/is (= 200 (:status delete-resp1)))
      (t/is (= 200 (:status delete-resp2)))
      (t/is (= "{\"result\":1}" (:body delete-resp1))) ;; number of rows deleted first time
      (t/is (= "{\"result\":0}" (:body delete-resp2)))))) ;; number of rows deleted after first delete
