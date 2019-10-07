(ns files.handlers
  (:import java.util.Base64)
  (:require [files.json :as json]
            [clojure.java.io :as io]
            [files.b64 :as b64]
            [cheshire.generate :refer [add-encoder encode-str]]
            [clojure.tools.logging :as log]
            [files.token :as token]
            [hiccup.core :refer [html]]
            ;; [hiccup.page :refer [html5]]
            [files.db :as db]))

;; keys that must be included in document when creating/updating
(def required-document-keys [:filename :mimetype :filedata])

;; Instant -> JSON generator support for cheshire
(add-encoder java.time.Instant
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (str c))))

(defn html-response
  "Return html response as response map. Takes hiccup html elements for html redering."
  [status & elements]
  {:status status
   :body (html elements)
   :headers {"Content-type" "text/html;charset=utf-8"}})

(defn error-key-msg [key]
  (str " [error-key: " key "] "))

(defn html-error
  "Return error response as html and write msg to log."
  [code exp msg]
  (let [error-key (format "%x" (.hashCode (java.time.Instant/now)))]
    (log/error exp msg (assoc (ex-data exp) :error-key error-key))
    (html-response code (str msg (error-key-msg error-key)))))

(defn json-response
  "Return json response as response map."
  [status data]
  {:status status
   :body (json/clj->json data)
   :headers {"Content-Type" "application/json"}})

(def json-ok (partial json-response 200))

(defn json-error
  "Return error response as json and write Error msg to log."
  [code exp msg]
  (let [error-key (format "%x" (.hashCode (java.time.Instant/now)))]
    (log/error exp msg (assoc (ex-data exp) :error-key error-key))
    (json-response code {:result (str msg (error-key-msg error-key))})))

(defn json-warn
  "Return error response as json and write Warn msg to log."
  [code exp msg]
  (let [error-key (format "%x" (.hashCode (java.time.Instant/now)))]
    (log/warn (str msg " " (.getMessage exp) (error-key-msg error-key)))
    (json-response code {:result (str msg (error-key-msg error-key))})))

(defn index
  "Main entry point which starts the adminapp."
  [request]
  (html [:html
         [:head
          [:meta {:charset "UTF-8"}]
          [:title "Files API"]
          [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}]]
         [:body
          [:div#app] [:script {:src "/js/main.js" :type "text/javascript" }]]]))

(defn get-auditlogs
  "Return auditlogs as json. Throws if error."
  [request fileid limit]
  (let [limit (if (pos-int? limit) limit
                  (try (Integer/parseInt limit)
                       (catch Exception e db/DEFAULT-QUERY-LIMIT)))]
    (try 
      (json-ok (into [] (db/get-auditlogs fileid limit)))
      (catch Exception e (json-error 400 e "failed to get auditlogs")))))

(defn get-documents
  "Return documents as vector. Throws if error."
  [request limit]
  (let [limit (if (pos-int? limit) limit
                  (try (Integer/parseInt limit)
                       (catch Exception e db/DEFAULT-QUERY-LIMIT)))]
    (into [] (db/get-documents limit (:identity request)))))

(defn get-documents-json
  "Return most recent documents up to limit as json response."
  [request limit]
  (try
    (json-ok (get-documents request limit))
    (catch Exception e (json-error 400 e "failed to get documents"))))

(defn get-document
  "Return document as json response, file data is dropped if binary? is 'false' or 'no'."
  ([request id]
   (get-document request id nil))
  ([request id binary?]
   (try
     (let [binary? (if (or (= "false" binary?)
                           (= "no" binary?)) false true)
           result (db/get-document id binary? (:identity request))]
       (if (nil? result)
         (json-response 404 {:result (str "Document not found, id: " id)})
         (json-response 200 result)))
     (catch Exception e (json-error 400 e "failed to get document")))))

(defn valid-json?
  "Returns true if input is valid json, false otherwise."
  [input]
  (try (json/json->clj input)
       true
       (catch Exception e false)))

(defn validate
  "Return true if document and identity are valid and throw if not."
  [document identity]
  (let [metadata (:metadata document)]
    (when-not (and (some? (:user identity))
                   (some? (:origin identity)))
      (throw (ex-info "user and/or origin key missing from identity" {:identity identity})))
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
  [request]
  (try
    (let [document (json/json->clj (slurp (:body request)))
          identity (:identity request)]
      (validate document identity)
      (json-ok (db/create-document document identity)))
    (catch Exception e (json-error 400 e (str "document creation failed: " (.getMessage e))))))

(defn close-document
  "close document and return close count as json response."
  [request id]
  (try
    (if (db/close-document id (:identity request))
      (json-ok {:result "success"})
      (throw (ex-info "close failed, maybe already closed or not found" {:id id})))
    (catch Exception e (json-error 400 e "document close failed"))))

(defn update-document
  "Update document and return update count as json response."
  [request id]
  (try
    (let [document (json/json->clj (slurp (:body request)))
          identity (:identity request)]
      (if (db/update-document id document identity)
        (json-ok {:result "success"})
        (throw (ex-info "update failed, maybe non-existing document" {:id id}))))
    (catch Exception e (json-error 400 e (str "document update failed: " (.getMessage e))))))

(defn login
  "Admin login: validates admin token and returns claims with response code 200 if token is valid admin token
  else returns response code 401."
  [request secret]
  (try
    (let [data (json/json->clj (slurp (:body request)))
          claims (token/validate (:token data) secret)]
      (if (= "admin" (:role claims))
        (json-ok claims)
        (json-response 401 {:result "Token is not a valid admin token!"})))
    (catch Exception e (json-warn 401 e "Invalid token!"))))

(defn download
  "Return file response with file's mime type set to content-type."
  [request id]
  (try
    (let [result (db/get-document id true (:identity request))]
      (if (nil? result)
        (html-response 404 (str "Document not found, id: " id))
        {:status 200
         :headers {"Content-type" (:mimetype result)}
         :body (io/input-stream (b64/decode (:filedata result)))}))
    (catch Exception e (html-error 400 e (str "File download failed: " (.getMessage e))))))
