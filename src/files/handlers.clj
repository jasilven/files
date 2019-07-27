(ns files.handlers
  (:import java.util.Base64)
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [files.db :as db]))

;; keys that must be included in document when creating/updating
(def required-document-keys [:file_name :mime_type :file_data])

;; (extend-type java.sql.Timestamp
;;   json/JSONWriter
;;   (-write [date out]
;;     (json/-write (str date) out)))

(extend-type java.time.Instant
  json/JSONWriter
  (-write [date out]
    (json/-write (str date) out)))

(defn json-response
  "Return json response as response map."
  [status data]
  {:status status
   :body (json/write-str data)
   :headers {"Content-Type" "application/json"}})

(def json-ok (partial json-response 200))

(defn json-error
  "Return error response as json and write msg to log."
  [code exp msg]
  (log/error exp msg)
  (json-response code {:result msg}))

(defn get-documents
  "Return documents as vector. Throws if error."
  [ds limit]
  (let [limit (if (pos-int? limit) limit
                  (try (Integer/parseInt limit)
                       (catch Exception e db/DEFAULT-QUERY-LIMIT)))]
    (into [] (db/get-documents ds limit))))

(defn get-documents-json
  "Return most recent documents up to limit as json response."
  [ds limit]
  (try
    (json-ok (get-documents ds limit))
    (catch Exception e (json-error 400 e "failed to get documents"))))

(defn get-document
  "Return document as json response, file data is included if binary? is true."
  [ds id binary?]
  (try
    (let [result (db/get-document ds id binary?)]
      (if (nil? result)
        (throw (ex-info "document not found" {:id id}))
        (json-response 200 result)))
    (catch Exception e (json-error 400 e "failed to get document"))))

(defn create-document
  "Create document and return id as json response."
  [ds request]
  (try
    (let [document (json/read-str (slurp (:body request)) :key-fn keyword)]
      (if (not-every? #(contains? document %) required-document-keys)
        (throw (ex-info "cannot create document, required field missing" {:request request}))
        (json-ok (db/create-document ds document))))
    (catch Exception e (json-error 400 e "document creation failed"))))

(defn delete-document
  "Delete document and return delete count as json response."
  [ds id]
  (try
    (if (db/delete-document ds id)
      (json-ok {:result "success"})
      (throw (ex-info "cannot delete twice, document already deleted" {:id id})))
    (catch Exception e (json-error 400 e "document delete failed"))))

(defn update-document
  "Update document and return update count as json response."
  [ds id request]
  (try
    (let [document (json/read-str (slurp (:body request)) :key-fn keyword)]
      (if (not-every? #(contains? document %) required-document-keys)
        (throw (ex-info "required field missing" {:id id :request request}))
        (if (db/update-document ds id document)
          (json-ok {:result "success"})
          (throw (ex-info "cannot update, maybe non-existing document" {:id id})))))
    (catch Exception e (json-error 400 e "document update failed"))))
