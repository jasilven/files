(ns files.db
  (:import org.postgresql.util.PGobject)
  (:require [next.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set  :as rs]
            [clojure.data.json :as json]))

(def files-table-create ["
CREATE TABLE files (id VARCHAR(36) PRIMARY KEY,
  created TIMESTAMP NOT NULL,
  updated TIMESTAMP,
  deleted TIMESTAMP,
  mime_type VARCHAR NOT NULL,
  file_name VARCHAR NOT NULL,
  file_data TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  category VARCHAR,
  metadata JSON)"])

(def auditlog-table-create ["
CREATE TABLE auditlog (id SERIAL PRIMARY KEY,
  created TIMESTAMP NOT NULL,
  fileid VARCHAR(36) REFERENCES files(id) NOT NULL,
  event VARCHAR(36) NOT NULL,
  userid VARCHAR(100) NOT NULL
  )"])

(def fields-except-file_data "id,created,updated,deleted,mime_type,file_name,file_size,category,metadata")
(def files-table-drop ["DROP TABLE files, auditlog cascade"])

(def DEFAULT-QUERY-LIMIT 100) ;; default query limit
(def MAX-QUERY-LIMIT 1000)    ;; max query limit

(defn map->pgjson
  "Converts map to postgres JSON object to allow saving it correctly into db"
  [m]
  (let [pgo (PGobject.)]
    (doto pgo
      (.setType "json")
      (.setValue (json/write-str m)))
    pgo))

;; Provide support for postgres native JSON data type
(extend-protocol rs/ReadableColumn
  PGobject
  (read-column-by-label ^clojure.lang.IPersistentMap [^PGobject pgo _]
    (let [type (.getType pgo)
          value (.getValue pgo)]
      (case type
        "json" (json/read-str value)
        :else value)))
  (read-column-by-index ^clojure.lang.IPersistentMap [^PGobject pgo _2 _3]
    (let [type (.getType pgo)
          value (.getValue pgo)]
      (case type
        "json" (json/read-str value :key-fn keyword)
        :else value)))

  java.sql.Timestamp
  (read-column-by-label ^java.time.Instant [^java.sql.Timestamp v _]
    (.toInstant v))
  (read-column-by-index ^java.time.Instant [^java.sql.Timestamp v _2 _3]
    (.toInstant v))
  )
;; (extend-protocol p/SettableParameter
;;   PGobject
;;   (read-column-by-label ^clojure.lang.IPersistentMap [^PGobject pgo _]
;;     (let [type (.getType pgo)
;;           value (.getValue pgo)]
;;       (case type
;;         "json" (json/read-str value)
;;         :else value)))
;;   (read-column-by-index ^clojure.lang.IPersistentMap [^PGobject pgo _2 _3]
;;     (let [type (.getType pgo)
;;           value (.getValue pgo)]
;;       (case type
;;         "json" (json/read-str value)
;;         :else value)))
;; java.sql.Timestamp
;; (read-column-by-label ^java.time.Instant [^java.sql.Timestamp v _]
;;   (.toInstant v))
;; (read-column-by-index ^java.time.Instant [^java.sql.Timestamp v _2 _3]
;;   (.toInstant v))
;; )

(defn uuid
  "Return new UUID as string or convert s to UUID. Throws if error."
  ([]
   (.toString (java.util.UUID/randomUUID)))
  ([s]
   (java.util.UUID/fromString s)))

(defn valid-id?
  "Return true if id is valid uuid else false."
  [id]
  (try
    (uuid? (java.util.UUID/fromString id))
    (catch Exception e false)))

(defn timestamp [] (java.sql.Timestamp. (.getTime (java.util.Date.))))

(defn get-datasource
  "Return pooled datasource."
  [config]
  (try
    (:datasource (pool/make-datasource-spec (:db-pool config)))
    (catch Exception e (.getMessage e))))

(defn db-connection?
  "Datasource connection test. Return true if db connection exists else false."
  [ds]
  (try (jdbc/execute-one! ds ["SELECT COUNT(1+1)"]) true
       (catch Exception e false)))

(defn files-exists?
  "Return true if files table exists or else false."
  [ds]
  (try (jdbc/execute-one! ds ["SELECT COUNT (*) FROM files"]) true
       (catch Exception e false)))

(defn create-files-table
  "Create files table and auditlog table. Throws if error."
  [ds]
  (jdbc/with-transaction [tx ds]
    (jdbc/execute! tx files-table-create)
    (jdbc/execute! tx auditlog-table-create)))

(defn drop-files-table
  "Drop files table and return true. Throws if error."
  [ds]
  (jdbc/execute! ds files-table-drop))

;; (defn create-document
;;   "Create document and return id of created document as map. Throws if error."
;;   [ds document]
;;   (let [meta (map->pgjson (:metadata document))
;;         id (uuid)
;;         ts (timestamp)
;;         size (count (:file_data document))]
;;     (sql/insert! ds :files (assoc document :id id :created ts :file_size size :metadata meta)
;;                  {:return-keys ["id"]})))

(defn auditlog!
  "Write audit log entry {:fileid :created :userid :event}. Throws if error."
  [tx entry]
  (sql/insert! tx :auditlog entry))

(defn create-document
  "Create document and return id of created document as map. Throws if error."
  [ds document]
  (let [ts (timestamp)
        doc (assoc document
                   :id (uuid)
                   :metadata (map->pgjson (:metadata document))
                   :created ts
                   :file_size (count (:file_data document)))]
    (jdbc/with-transaction [tx ds]
      (let [result (sql/insert! tx :files doc {:return-keys ["id"]})]
        (auditlog! tx {:fileid (:id doc) :created ts :userid "unknown" :event "create"})
        result))))

(defn update-document
  "Update document and return true on success. Throws if error."
  [ds id document]
  (let [ts (timestamp)
        doc (assoc (dissoc document :id :created :deleted)
                   :metadata (map->pgjson (:metadata document))
                   :updated ts
                   :file_size (count (:file_data document)))]
    (jdbc/with-transaction [tx ds]
      (let [result (sql/update! tx :files doc {:id id})]
        (when (zero? (:next.jdbc/update-count result))
          (ex-info "document update failed, maybe non-existing document" {:id id :document document}))
        (auditlog! tx {:fileid id :created ts :userid "unknown" :event "update"})
        true))))

(defn delete-document
  "Mark document deleted with timestamp and return true on success and false
  if document already deleted. Throws if error."
  [ds id]
  (let [doc (jdbc/execute-one! ds ["SELECT deleted FROM files WHERE id = ?" id])]
    (when (nil? doc) (throw (ex-info "cannot delete, document not found" {:id id})))
    (if (nil? (:files/deleted doc))
      (jdbc/with-transaction [tx ds]
        (let [ts (timestamp)
              result (sql/update! tx :files {:deleted ts} {:id id})]
          (when (zero? (:next.jdbc/update-count result))
            (ex-info "document delete failed" {:id id :document doc}))
          (auditlog! tx {:fileid id :created ts :userid "unknown" :event "delete"})
          true))
      false)))

(defn undelete-document
  "Undelete document by setting deleted timestamp nil and return true on success and false
  if document already undeleted. Throws if error."
  [ds id]
  (let [doc (jdbc/execute-one! ds ["SELECT deleted FROM files WHERE id = ?" id])]
    (when (nil? doc) (throw (ex-info "cannot undelete, document not found" {:id id})))
    (if-not (nil? (:files/deleted doc))
      (jdbc/with-transaction [tx ds]
        (let [ts (timestamp)
              result (sql/update! tx :files {:deleted nil} {:id id})]
          (when (zero? (:next.jdbc/update-count result))
            (ex-info "document undelete failed" {:id id :document doc}))
          (auditlog! tx {:fileid id :created ts :userid "unknown" :event "undelete"})
          true))
      false)))

(defn get-documents
  "Return vector of documents (excluding file data) up to limit or
  up to MAX-QUERY-LIMIT if limit is greater than MAX-QUERY-LIMIT. Throws if error."
  ([ds] (get-documents ds DEFAULT-QUERY-LIMIT))
  ([ds limit]
   (if (pos-int? limit)
     (sql/query ds [(str "SELECT " fields-except-file_data " FROM files ORDER BY created DESC LIMIT ?")
                    (min limit MAX-QUERY-LIMIT)])
     (throw (ex-info "invalid parameter value for limit" {:limit limit})))))

(defn get-document
  "Return document as map or nil if not found. File data included if binary? is true. Throws if error."
  [ds id binary?]
  (jdbc/with-transaction [tx ds]
    (let [ts (timestamp)
          result (if binary?
                   (sql/get-by-id tx :files id)
                   (jdbc/execute-one! tx [(str "SELECT " fields-except-file_data " FROM files WHERE id = ?") id]))]
      (when-not (nil? result) (auditlog! tx {:fileid id :created ts :userid "unknown" :event "read"}))
      result)))

(defn get-auditlogs
  "Return vector of log entries up to limit or
  up to MAX-QUERY-LIMIT if limit is greater than MAX-QUERY-LIMIT. Throws if error."
  ([ds fileid] (get-auditlogs ds fileid DEFAULT-QUERY-LIMIT))
  ([ds fileid limit]
   (if (pos-int? limit)
     (sql/query ds ["SELECT * FROM auditlog WHERE fileid = ? ORDER BY created DESC LIMIT ?"
                    fileid
                    (min limit MAX-QUERY-LIMIT)])
     (throw (ex-info "invalid parameter value for limit" {:limit limit})))))
