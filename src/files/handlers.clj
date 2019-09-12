(ns files.handlers
  (:import java.util.Base64)
  (:require [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder encode-str]]
            [clojure.tools.logging :as log]
            [files.db :as db]))

;; keys that must be included in document when creating/updating
(def required-document-keys [:file_name :mime_type :file_data])

;; Instant -> JSON generator support for cheshire
(add-encoder java.time.Instant
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (str c))))

(defn json-response
  "Return json response as response map."
  [status data]
  {:status status
   :body (json/generate-string data)
   :headers {"Content-Type" "application/json"}})

(def json-ok (partial json-response 200))

(defn json-error
  "Return error response as json and write msg to log."
  [code exp msg]
  (let [error-key (format "%x" (.hashCode (java.time.Instant/now)))]
    (log/error exp msg (assoc (ex-data exp) :error-key error-key))
    (json-response code {:result (str msg ", error-key: " error-key)})))

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

;; (defn required-keys-ok?
;;   "Return true if document contains required keys and they are non-empty and false otherwise."
;;   [document]
;;   (every? #(and (contains? document %)
;;                 (not-empty (get document %))) required-document-keys))

(defn valid-json?
  "Returns true if input is valid json, false otherwise."
  [input]
  (try (json/parse-string input)
       true
       (catch Exception e false)))

(defn validate-document
  "Return true if document is valid and throw if not."
  [document]
  (let [metadata (:metadata document)]
    (when (not-every? #(and (contains? document %)
                            (not-empty (get document %))) required-document-keys)
      (throw (ex-info "required key missing" {:document-keys (keys document)
                                              :required-keys required-document-keys})))
    (when-not (or (nil? metadata)
                  (valid-json? metadata))
      (throw (ex-info "invalid JSON in metadata" {:metadata metadata}))))
  true)

(defn create-document
  "Create document and return id as json response."
  [ds request]
  (try
    (let [document (json/parse-string (slurp (:body request)) true)]
      (validate-document document)
      (json-ok (db/create-document ds document)))
    (catch Exception e (json-error 400 e (str "document creation failed: " (.getMessage e))))))

(defn close-document
  "close document and return close count as json response."
  [ds id]
  (try
    (if (db/close-document ds id)
      (json-ok {:result "success"})
      (throw (ex-info "cannot close twice, document already closed" {:id id})))
    (catch Exception e (json-error 400 e "document close failed"))))

(defn update-document
  "Update document and return update count as json response."
  [ds id request]
  (try
    (let [document (json/parse-string (slurp (:body request)) true)]
      (validate-document document)
      (if (db/update-document ds id document)
        (json-ok {:result "success"})
        (throw (ex-info "cannot update, maybe non-existing document" {:id id}))))
    (catch Exception e (json-error 400 e "document update failed"))))
