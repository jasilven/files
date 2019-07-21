(ns files.db
  (:require [next.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool]
            [next.jdbc.sql :as sql]))

(def files-table-create ["
CREATE TABLE files (id CHARACTER VARYING(255) PRIMARY KEY,
  created TIMESTAMP NOT NULL,
  mime_type CHARACTER VARYING(255) NOT NULL,
  file_name CHARACTER VARYING(255) NOT NULL,
  file_data BYTEA NOT NULL)"])

(def files-table-drop ["DROP TABLE files"])

(def DEFAULT-LIMIT 100) ;; default query limit
(def MAX-LIMIT 1000) ;; max query limit

(defn uuid [] (.toString (java.util.UUID/randomUUID)))
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
  "return true if db connection exists else false"
  [db]
  (try (jdbc/execute-one! db ["SELECT COUNT(1+1)"]) true
       (catch Exception e false)))

(defn files-exists?
  "return true if files table exists or else false"
  [db]
  (try (jdbc/execute-one! db ["SELECT COUNT (*) FROM files"]) true
       (catch Exception e false)))

(defn create-files-table
  "create files table. Throws if error."
  [db]
  (jdbc/execute! db files-table-create))

(defn drop-files-table
  "drop files table and return true. Throws if error."
  [db]
  (jdbc/execute! db files-table-drop))

(defn create-file
  "create file and return id of created file as map. Throws if error."
  [db file-name mime-type file-data]
  (sql/insert! db :files {:id (uuid)
                          :created (timestamp)
                          :file_name file-name
                          :mime_type mime-type
                          :file_data file-data} {:return-keys ["id"]}))
(defn delete-file
  "delete file. Throws if error."
  [db id]
  (if (valid-id? id)
    (jdbc/execute-one! db ["DELETE FROM files WHERE id =?" id])
    (throw (Exception. (str "invalid file id: " id)))))

(defn get-files
  "returns list of files (excluding file data) up to limit or
  up to MAX-LIMIT if limit is greater than MAX-LIMIT. Throws if error."
  ([db] (get-files db DEFAULT-LIMIT))
  ([db limit]
   (if (pos-int? limit)
     (sql/query db ["SELECT id, created, file_name, mime_type FROM files ORDER BY created DESC LIMIT ?"
                    (min limit MAX-LIMIT)])
     (throw (Exception. (str "invalid limit: " limit))))))

(defn get-file
  "return file as map or nil if not found. File data included if binary? is true. Throws if error."
  [db id binary?]
  (if (valid-id? id)
    (if binary?
      (jdbc/execute-one! db ["SELECT id, created, file_name, mime_type, file_data FROM files WHERE id = ?" id])
      (jdbc/execute-one! db ["SELECT id, created, file_name, mime_type FROM files WHERE id = ?" id]))
    (throw (Exception. (str "invalid file id: " id)))))
