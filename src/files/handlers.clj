(ns files.handlers
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

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn response [status & elements]
  {:status status
   :body (html elements)
   :headers {"Content-Type" "text/html;charset=utf-8"}})

(defn json-response [status data]
  {:status status
   :body (json/write-str data)
   :headers {"Content-Type" "application/json"}})

(def ok (partial response 200))

(defn error
  [msg]
  (log/error msg)
  (let [emsg [:html [:meta {:charset "UTF-8"}]
              [:body [:h3 "Error"] [:pre msg]]]]
    (response 400 emsg)))

(def json-ok (partial json-response 200))

(defn json-error
  [msg]
  (log/error msg)
  (json-response 400 {:result msg}))

(defn get-files
  "return files as vector. throws if error"
  [db limit]
  (let [limit (if (pos-int? limit) limit
                  (try (Integer/parseInt limit) (catch Exception e db/DEFAULT-LIMIT)))]
    (into [] (db/get-files db limit))))

(defn get-files-json
  "return files as json response"
  [db limit]
  (try
    (json-ok (get-files db limit))
    (catch Exception e (json-error (.getMessage e)))))

(defn get-file-info
  "return file info (excluding the file itself) as json response"
  [db id]
  (try
    (let [result (db/get-file db id false)]
      (if (nil? result)
        (json-response 404 {:result (str "not found, id: " id)})
        (json-ok result)))
    (catch Exception e (json-error (.getMessage e)))))

(defn get-file
  "return file response with file's mime type set to content-type"
  [db id]
  (try
    (let [result (db/get-file db id true)]
      (if (nil? result)
        (json-response 404 {:result (str "not found, id: " id)})
        (do
          (spit "result.txt" result)
          {:status 200
           :headers {"Content-type" (:files/mime_type result)}
           :body (io/input-stream (:files/file_data result))})))
    (catch Exception e (json-error (.getMessage e)))))

(defn create-file
  "create file and return json response"
  [db request]
  (try
    (let [file-param (get (:params request) "file")
          file-name (:filename file-param)
          content-type (:content-type file-param)]
      (if (empty? file-name)
        (json-error (str "file missing"))
        (let [file-data (file->bytes (:tempfile file-param))
              result (db/create-file db file-name content-type file-data)]
          (log/info "created result:" (dissoc result :file_data))
          (json-ok (dissoc result :file_data)))))
    (catch Exception e (json-error (.getMessage e)))))

(defn delete-file
  "delete file and return delete count as json response"
  [ds id]
  (try
    (json-ok {:result (first (db/delete-file ds id))})
    (catch Exception e (json-error (.getMessage e)))))

(defn admin
  "admin page"
  [ds config api-uri shutdown-uri]
  (try (ok (views/admin (get-files ds 50) config api-uri shutdown-uri))
       (catch Exception e (error (.getMessage e)))))
