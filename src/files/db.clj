(ns files.db
  (:require [next.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool]
            [next.jdbc.sql :as sql]))

(def files-table-create ["
CREATE TABLE files (id VARCHAR(36) PRIMARY KEY,
  created TIMESTAMP NOT NULL,
  updated TIMESTAMP,
  mime_type VARCHAR NOT NULL,
  file_name VARCHAR NOT NULL,
  file_data TEXT NOT NULL)"])

(def files-table-drop ["DROP TABLE files"])

(def DEFAULT-LIMIT 100) ;; default query limit
(def MAX-LIMIT 1000)    ;; max query limit

(defn uuid
  "return new UUID as string or convert s to UUID. Throws if error."
  ([]
   (.toString (java.util.UUID/randomUUID)))
  ([s]
   (java.util.UUID/fromString s)))

(defn valid-id?
  "return true if id is valid uuid else false"
  [id]
  (try
    (uuid? (java.util.UUID/fromString id))
    (catch Exception e false)))

(defn timestamp [] (java.sql.Timestamp. (.getTime (java.util.Date.))))

(defn get-datasource
  "return pooled datasource"
  [config]
  (try
    (:datasource (pool/make-datasource-spec (:db-pool config)))
    (catch Exception e (.getMessage e))))

(defn db-connection?
  "db connection test. Return true if db connection exists else false"
  [ds]
  (try (jdbc/execute-one! ds ["SELECT COUNT(1+1)"]) true
       (catch Exception e false)))

(defn files-exists?
  "return true if files table exists or else false"
  [ds]
  (try (jdbc/execute-one! ds ["SELECT COUNT (*) FROM files"]) true
       (catch Exception e false)))

(defn create-files-table
  "create files table. Throws if error."
  [ds]
  (jdbc/execute! ds files-table-create))

(defn drop-files-table
  "drop files table and return true. Throws if error."
  [ds]
  (jdbc/execute! ds files-table-drop))

(defn create-document
  "create document and return id of created document as map. Throws if error."
  [ds document]
  (sql/insert! ds :files (assoc document
                                :id (uuid)
                                :created (timestamp)) {:return-keys ["id"]}))

(defn update-document
  "update document and return update count. Throws if error."
  [ds id document]
  (sql/update! ds :files (-> (dissoc document :id)
                             (assoc :updated (timestamp)))
               {:id id}))

(defn delete-document
  "delete document and return update count. Throws if error."
  [ds id]
  (sql/delete! ds :files {:id id}))

(defn get-documents
  "returns list of documents (excluding file data) up to limit or
  up to MAX-LIMIT if limit is greater than MAX-LIMIT. Throws if error."
  ([ds] (get-documents ds DEFAULT-LIMIT))
  ([ds limit]
   (if (pos-int? limit)
     (sql/query ds ["SELECT id, created, updated, file_name, mime_type FROM files ORDER BY created DESC LIMIT ?"
                    (min limit MAX-LIMIT)])
     (throw (Exception. (str "invalid limit: " limit))))))

(defn get-document
  "return document as map or nil if not found. File data included if binary? is true. Throws if error."
  [ds id binary?]
  (if binary?
    (jdbc/execute-one! ds ["SELECT id, created, updated, file_name, mime_type, file_data FROM files WHERE id = ?" id])
    (jdbc/execute-one! ds ["SELECT id, created, updated, file_name, mime_type FROM files WHERE id = ?" id])))
