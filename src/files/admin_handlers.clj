(ns files.admin-handlers
  (:require [clojure.java.io :as io]
            [files.views :as views]
            [files.b64 :as b64]
            [clojure.tools.logging :as log]
            [files.db :as db]
            [ring.util.response :refer [redirect]]
            [hiccup.core :refer [html]]))

(def DEFAULT-LOG-LIMIT 20)

(defn response
  "Return html response as response map."
  [status & elements]
  {:status status
   :body (html elements)
   :headers {"Content-Type" "text/html;charset=utf-8"}})

(def ok (partial response 200))

(defn error
  "Handle error by showing error page and logging error message."
  [exp msg]
  (log/error exp msg)
  {:status 400
   :body (html (views/error exp msg))
   :headers {"Content-Type" "text/html;charset=utf-8"}})

(defn download
  "Return file response with file's mime type set to content-type."
  [ds id]
  (try
    (let [result (db/get-document ds id true)]
      (if (nil? result)
        (response 404 (str "Document not found, id: " id))
        {:status 200
         :headers {"Content-type" (:files/mime_type result)}
         :body (io/input-stream (b64/decode (:files/file_data result)))}))
    (catch Exception e (error e "file download failed"))))


;; TODO: add unit test
(defn upload
  "Create new document from form upload."
  [ds request]
  (try
    (let [file-param (get (:params request) "file")
          file_name (:filename file-param)]
      (if (empty? file_name)
        (throw (ex-info "upload error, input file missing" {:request request}))
        (let [file_data (b64/encode-file (:tempfile file-param))
              _ (db/create-document ds {:file_name file_name
                                        :mime_type (:content-type file-param)
                                        :file_data file_data})]
          (redirect "/admin"))))
    (catch Exception e (error e "document upload failed"))))

;; TODO: add unit test
(defn details
  "Render document details."
  [ds id]
  (try
    (let [document (db/get-document ds id false)]
      (if (nil? document)
        (throw (ex-info "document not found" {:id id}))
        (ok (views/details document (db/get-auditlogs ds id DEFAULT-LOG-LIMIT)))))
    (catch Exception e (error e "unable to show document details"))))

;; TODO: add unit test
(defn delete
  "Mark document deleted."
  [ds id]
  (try
    (db/delete-document ds id)
    (ok (views/details (db/get-document ds id false) (db/get-auditlogs ds id DEFAULT-LOG-LIMIT)))
    (catch Exception e (error e "document deletion failed"))))

;; TODO: add unit test
(defn undelete
  "Mark document undeleted."
  [ds id]
  (try
    (db/undelete-document ds id)
    (ok (views/details (db/get-document ds id false) (db/get-auditlogs ds id DEFAULT-LOG-LIMIT)))
    (catch Exception e (error e "document undelete failed"))))

(defn main
  "Render admin page."
  [ds config]
  (try (ok (views/admin (db/get-documents ds 50) config))
       (catch Exception e (error e "unable to show main page"))))
