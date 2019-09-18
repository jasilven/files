(ns testclient
  (:import java.util.Base64)
  (:require [clj-http.client :as client]
            [files.b64 :as b64]
            [clojure.java.io :as io]
            [files.token :as token]
            [files.json :as json]))

(def uri "https://localhost:8080")
(def user-token (token/generate {:user "" :origin "" :secret "" :days 1}))
(def auth-options {:insecure? true :oauth-token user-token})
(def metadata (json/clj->json {:title "lorem ipsum dolor"
                               :owner "Gene Roddenberry"
                               :creator "Ian Fleming"
                               :type "agreement"
                               :date "2020-12-12"
                               :status "draft"
                               :version 3
                               :sensitivity "normal"
                               :importance "urgent"
                               :customerID "A-95878649"
                               :sector "B2B"}))

(def pdf-document (json/clj->json {:filename "testfile.pdf"
                                   :mimetype "application/pdf"
                                   :filedata (b64/encode-file "test/testfile.pdf")
                                   :metadata metadata}))

(def jpg-document (json/clj->json {:filename "testfile.jpg"
                                   :mimetype "image/jpg"
                                   :filedata (b64/encode-file "test/testfile.jpg")
                                   :metadata metadata}))

(def invalid-metadata-document (json/clj->json {:filename "testfile.jpg"
                                                :mimetype "image/jpg"
                                                :filedata (b64/encode-file "test/testfile.jpg")
                                                :metadata "kuraa"}))

(def non-metadata-document (json/clj->json {:filename "non-metadata-document.jpg"
                                            :mimetype "image/jpg"
                                            :filedata (b64/encode-file "test/testfile.jpg")}))
(defn spy [x] (println x) x)

(defn post-document
  "post document and return document id"
  [document]
  (-> (client/post (str uri "/api/files") (merge auth-options {:content-type :json :body document}))
      :body
      (json/json->clj)
      :id))

(defn get-documents
  "return list documents"
  []
  (-> (client/get (str uri "/api/files") auth-options)
      :body
      (json/json->clj)))

(comment
  (get-documents)
  ;;
  )

(defn get-document
  "return document by id"
  [id]
  (-> (client/get (str uri "/api/files/" id) auth-options)
      :body
      (json/json->clj)))

(defn download-document
  "return document by id"
  [id]
  (-> (client/get (str uri "/api/files/" id "/download") (assoc auth-options :as :byte-array)) :body))

(defn update-document
  "update document by id"
  [id document]
  (try
    (-> (client/put (str uri "/api/files/" id) (merge auth-options {:content_type :json :body document}))
        :body
        (json/json->clj))
    (catch Exception e (println (.getMessage e) (ex-data e)))))

(defn write-document
  "write document to file"
  [id path]
  (-> (get-document id)
      :filedata
      (b64/decode)
      (clojure.java.io/copy (java.io.File. path))))

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
    (let [resp (client/get (str uri "/api/files") auth-options)
          docs (json/json->clj (:body resp))
          doc-cnt (count test-documents)]
      (doseq [doc docs]
        (update-document (:id doc) (get test-documents (rand-int doc-cnt)))))
    (catch Exception e (println (.getMessage e) (ex-data e)))))

(comment
  ;; post one document
  (post-document pdf-document)

  ;; get documents as vector
  (into [] (get-documents))

  ;; post and get
  (-> (post-document jpg-document) (get-document) (dissoc :filedata))

  ;; post and download
  (-> (post-document jpg-document) (download-document) (clojure.java.io/copy (java.io.File. "out.jpg")))

  ;; post, get and write document to file
  (let [id (post-document pdf-document)]
    (update-document id jpg-document)
    (get-document id)
    (write-document id "out.jpg"))

  ;; generate n documents
  (time (generate-documents [pdf-document jpg-document] 10))

  ;; randomly update all documents
  (update-documents [pdf-document jpg-document])

  ;; auth test
  (client/get (str uri "/api/files") (merge auth-options {:content-type :json :throw-exceptions false}))

  (client/get (str uri "/admin") (merge auth-options {:content-type :json :throw-exceptions false}))

  ;; This should return 400 and error response!
  ;; try to post document with invalid metadata json
  (client/post (str uri "/api/files")
               (merge auth-options
                      {:content-type :json
                       :throw-exceptions false
                       :body invalid-metadata-document}))

  ;; non metadata post, should work
  (:body (client/post (str uri "/api/files")
                      (merge auth-options
                             {:content-type :json
                              :throw-exceptions false
                              :body non-metadata-document})))
  )
