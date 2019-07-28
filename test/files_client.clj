(ns files-client
  (:import java.util.Base64)
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(def api-uri "https://localhost:8080/api/files")
(def auth-options {:basic-auth ["apiuser" "1234"] :insecure? true})

(defn encode-b64 [barray]
  (String. (.encode (Base64/getEncoder) barray) "UTF-8"))

(defn decode-b64 [s]
  (.decode (Base64/getDecoder) s))

(defn file->bytes
  "open fname as file and return bytes"
  [fname]
  (with-open [xin (io/input-stream (io/file fname))
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(def metadata {:title "lorem ipsum dolor"
               :owner "Gene Roddenberry"
               :creator "Ian Fleming"
               :type "agreement"
               :date "2020-12-12"
               :status "draft"
               :version 3
               :sensitivity "normal"
               :importance "urgent"
               :customerID "A-95878649"
               :sector "B2O"})

(def pdf-document (json/generate-string {:file_name "testfile.pdf"
                                         :mime_type "application/pdf"
                                         :file_data (encode-b64 (file->bytes "test/testfile.pdf"))
                                         :metadata metadata}))

(def jpg-document (json/generate-string {:file_name "testfile.jpg"
                                         :mime_type "image/jpg"
                                         :file_data (encode-b64 (file->bytes "test/testfile.jpg"))
                                         :metadata metadata}))

(defn spy [x] (println x) x)

(defn post-document
  "post document and return document id"
  [document]
  (-> (client/post api-uri (merge auth-options {:content-type :json :body document}))
      :body
      (json/parse-string true)
      :id))

(defn get-documents
  "return list documents"
  []
  (-> (client/get api-uri auth-options)
      :body
      (json/parse-string true)))

(defn get-document
  "return document by id"
  [id]
  (-> (client/get (str api-uri "/" id) auth-options)
      :body
      (json/parse-string true)))

(defn update-document
  "update document by id"
  [id document]
  (try
    (-> (client/put (str api-uri "/" id) (merge auth-options {:content_type :json :body document}))
        :body
        (json/parse-string true))
    (catch Exception e (println (.getMessage e) (ex-data e)))))

(defn write-document
  "write document to file"
  [id fname]
  (-> (get-document id)
      :file_data
      (decode-b64)
      (clojure.java.io/copy (java.io.File. fname))))

(defn generate-documents
  "post document from documents vector randomly n times"
  [documents n]
  (try
    (let [doc-cnt (count documents)]
      (dotimes [_ n]
        (post-document (get documents (rand-int doc-cnt)))))
    (catch Exception e (println (.getMessage e) (ex-data e)))))

(defn update-documents
  "randomly update all documents to one in test-documents vector"
  [test-documents]
  (try
    (let [resp (client/get api-uri auth-options)
          docs (json/parse-string (:body resp) true)
          doc-cnt (count test-documents)]
      (doseq [doc docs]
        (update-document (:id doc) (get test-documents (rand-int doc-cnt)))))
    (catch Exception e (println (.getMessage e) (ex-data e)))))

(comment
  ;; post one document
  (post-document jpg-document)

  ;; get documents as vector
  (into [] (get-documents))

  ;; post and get
  (-> (post-document jpg-document) (get-document) (dissoc :file_data))

  ;; post, get and write document to file
  (let [id (post-document pdf-document)]
    (update-document id jpg-document)
    (get-document id)
    (write-document id "out.jpg"))

  ;; generate n documents
  (time (generate-documents [pdf-document jpg-document] 100))

  ;; randomly update all documents
  (update-documents [pdf-document jpg-document])
  ;;
  )
