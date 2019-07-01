(ns files.handlers
  (:require [hiccup.core :refer [html]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [files.db :as db]))

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
(def error (partial response 400))
(def json-ok (partial json-response 200))
(defn json-error [msg] (json-response 400 {:error msg}))

(defn get-files
  "return files as vector or as json response if to-json is true"
  [db limit to-json]
  (try
    (let [result (into [] (db/get-files db (if (nil? limit) 100 limit)))]
      (if to-json
        (json-ok (map #(dissoc % :file_data) result))
        result))
    (catch Exception e (json-error (.getMessage e)))))

(defn get-file-info
  "return file info as json response"
  [db id]
  (try
    (let [result (db/get-file db id)]
      (json-ok (dissoc result :file_data)))
    (catch Exception e (json-error (.getMessage e)))))

(defn get-file
  "return file response with files mime type set to content-type"
  [db id]
  (try
    (let [result (db/get-file db id)]
      {:status 200
       :headers {"Content-type" (:mime_type result)}
       :body (io/input-stream (:file_data result))})
    (catch Exception e (json-error (.getMessage e)))))

(defn create-file
  "create file and return json response for created file"
  [db request]
  (let [file-param (get (:params request) "file")
        file-name (:filename file-param)
        content-type (:content-type file-param)
        file-data (file->bytes (:tempfile file-param))]
    (if (or (empty? file-name)
            (empty? file-data))
      (json-error (str "file missing or empty file"))
      (try
        (let [result (db/create-file db file-name content-type file-data)]
          (json-ok (dissoc result :file_data)))
        (catch Exception e (json-error (.getMessage e)))))))

(defn delete-file
  "delete file and return json response"
  [db id]
  (try
    (let [result (into [] (db/delete-file db id))]
      (json-ok (str (first result))))
    (catch Exception e (json-error (.getMessage e)))))

(defn index
  "return main page as html"
  [db uri]
  (if (db/db-connection? db)
    (ok [:html [:meta {:charset "UTF-8"}]
         [:body
          [:h3 "Database"]
          [:pre (:dbtype db) " at " (:host db) ":" (:port db) " using database " (:dbname db)]
          [:h3 "Upload"]
          [:form {:action uri :method "post" :enctype "multipart/form-data"}
           [:input {:name "file" :type "file" :size "40"}]
           [:input {:type "submit" :name "submit" :value "submit"}]]
          [:h3 "Recent files"]
          [:ul
           (for [file (get-files db 50 false)]
             [:li
              [:a {:href (str uri "/" (:id file))} (:file_name file)] " "
              (:created file) " "
              [:i (:mime_type file)]])]]])
    (error [:html [:meta {:charset "UTF-8"}]
            [:body
             [:h3 "No db connection available !"]
             [:pre "Trying to use " (:dbtype db) " at " (:host db) ":" (:port db) " and database " (:dbname db)]]])))

