(ns files.db
  (:require [clojure.java.jdbc :as sql]))

(def table-ddl
  [[:id "CHARACTER VARYING(255) PRIMARY KEY"]
   [:created "TIMESTAMP NOT NULL"]
   [:mime_type "CHARACTER VARYING(255) NOT NULL"]
   [:file_name "CHARACTER VARYING(255) NOT NULL"]
   [:file_data "BYTEA NOT NULL"]])

(def DEFAULT-LIMIT 100) ;; default query limit
(def MAX-LIMIT 1000) ;; max query limit

(defn uuid [] (.toString (java.util.UUID/randomUUID)))
(defn valid-id? [id] (uuid? (java.util.UUID/fromString id)))
(defn timestamp [] (java.sql.Timestamp. (.getTime (java.util.Date.))))

(defn db-connection?
  "return true if db connection exists or false otherwise"
  [db]
  (try (sql/query db ["SELECT COUNT(1+1)"]) true
       (catch Exception e false)))

(defn files-exists?
  "return true if files table exists or false otherwise"
  [db]
  (try (sql/query db ["SELECT COUNT (*) FROM files"]) true
       (catch Exception e false)))

(defn create-files-table
  "create files table. Throws if error."
  [db]
  (let [sql-command (sql/create-table-ddl :files table-ddl)]
    (sql/db-do-commands db [sql-command])))

(defn drop-files-table
  "drop files table and return true. Throws if error."
  [db]
  (let [sql-command (sql/drop-table-ddl :files)]
    (sql/db-do-commands db [sql-command])))

(defn create-file
  "create file and return map of created file. Throws if error."
  [db file-name mime-type file-data]
  (first (sql/insert! db :files {:id (uuid)
                                 :created (timestamp)
                                 :file_name file-name
                                 :mime_type mime-type
                                 :file_data file-data})))
(defn delete-file
  "delete file. Throws if error."
  [db id]
  (if (valid-id? id)
    (sql/execute! db ["DELETE FROM files WHERE id = ?" id])
    (throw (Exception. "invalid file id"))))

(defn get-files
  "returns list of files up to limit or up to MAX-LIMIT if limit is greater than MAX-LIMIT. Throws if error."
  [db limit]
  (if (pos-int? limit)
    (sql/query db ["SELECT id, created, file_name, mime_type FROM files ORDER BY created DESC LIMIT ?"
                   (min limit MAX-LIMIT)])
    (throw (Exception. "invalid limit"))))

(defn get-file
  "return file map or nil if not found. File data included if binary? is true. Throws if error."
  [db id binary?]
  (if (valid-id? id)
    (let [query (if binary?
                  "SELECT * FROM files WHERE id = ?"
                  "SELECT id, created, file_name, mime_type FROM files WHERE id = ?")
          result (sql/query db [query id])]
      (if (nil? result) nil
          (first result)))
    (throw (Exception. "File not found, maybe invalid file id"))))
