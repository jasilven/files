(ns files.handlers
  (:import java.util.Base64)
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [files.views :as views]
            [clojure.tools.logging :as log]
            [files.db :as db]
            [hiccup.core :refer [html]]))

(extend-type java.sql.Timestamp
  json/JSONWriter
  (-write [date out]
    (json/-write (str date) out)))

(defn encode-b64
  "byte array to base64 string"
  [barray]
  (String. (.encode (Base64/getEncoder) barray) "UTF-8"))

(defn decode-b64
  "decode base64 string"
  [s]
  (.decode (Base64/getDecoder) s))

(defn file->bytes
  "open fname as file and return bytes"
  [fname]
  (with-open [xin (io/input-stream (io/file fname))
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn response
  "return html response as response map"
  [status & elements]
  {:status status
   :body (html elements)
   :headers {"Content-Type" "text/html;charset=utf-8"}})

(defn json-response
  "return json response as response map"
  [status data]
  {:status status
   :body (json/write-str data)
   :headers {"Content-Type" "application/json"}})

(def ok (partial response 200))

(defn error
  "return error (400) as html and write msg to log"
  [msg]
  (log/error msg)
  (let [emsg [:html [:meta {:charset "UTF-8"}]
              [:body [:h3 "Error"] [:pre msg]]]]
    (response 400 emsg)))

(def json-ok (partial json-response 200))

(defn json-error
  "return error (400) response as json and write msg to log"
  [msg]
  (log/error msg)
  (json-response 400 {:result msg}))

(defn get-documents
  "return documents as vector. throws if error"
  [db limit]
  (let [limit (if (pos-int? limit) limit
                  (try (Integer/parseInt limit) (catch Exception e db/DEFAULT-LIMIT)))]
    (into [] (db/get-documents db limit))))

(defn get-documents-json
  "return most recent documents up to limit as json response"
  [ds limit]
  (try
    (json-ok (get-documents ds limit))
    (catch Exception e (json-error (.getMessage e)))))

(defn get-document
  "return document as json response, file data is included if binary? is true"
  [ds id binary?]
  (try
    (let [result (db/get-document ds id binary?)]
      (if (nil? result)
        (json-response 404 {:result (str "not found, id: " id)})
        (json-response 200 result)))
    (catch Exception e (json-error (.getMessage e)))))

(defn create-document
  "create document and return id as json response"
  [ds request]
  (try
    (let [document (json/read-str (slurp (:body request)) :key-fn keyword)]
      (if (not-every? #(contains? document %) [:file_name :mime_type :file_data])
        (json-error (str "required field missing"))
        (json-ok (db/create-document ds document))))
    (catch Exception e (json-error (.getMessage e)))))

(defn delete-document
  "delete document and return delete count as json response"
  [ds id]
  (try
    (json-ok {:result (db/delete-document ds id)})
    (catch Exception e (json-error (.getMessage e)))))

(defn update-document
  "update document and return update count as json response"
  [ds id request]
  (try
    (let [document (json/read-str (slurp (:body request)) :key-fn keyword)]
      (if (not-every? #(contains? document %) [:file_name :mime_type :file_data])
        (json-error (str "required field missing"))
        (json-ok {:result (db/update-document ds id document)})))
    (catch Exception e (json-error (.getMessage e)))))

(defn download-document
  "return file response with file's mime type set to content-type"
  [db id]
  (try
    (let [result (db/get-document db id true)]
      (if (nil? result)
        (response 404 (str "Document not found, id: " id))
        {:status 200
         :headers {"Content-type" (:files/mime_type result)}
         :body (io/input-stream (decode-b64 (:files/file_data result)))}))
    (catch Exception e (json-error (.getMessage e)))))

(defn admin
  "admin page"
  [ds config]
  (try (ok (views/admin (get-documents ds 50) config))
       (catch Exception e (error (.getMessage e)))))

;; TODO: multipart post, is this needed?
;; (defn create-file
;;   "create file and return json response"
;;   [db request]
;;   (try
;;     (let [file-param (get (:params request) "file")
;;           file_name (:filename file-param)]
;;       (if (empty? file_name)
;;         (json-error (str "file missing"))
;;         (let [file_data (file->bytes (:tempfile file-param))
;;               result (db/create-file db {:file_name file_name
;;                                          :mime_type (:content-type file-param)
;;                                          :file_data file_data})]
;;           (json-ok result))))
;;     (catch Exception e (json-error (.getMessage e)))))
