(ns files.db
  (:require [clojure.java.jdbc :as sql]))

(def table-ddl
  [[:id "CHARACTER VARYING(255) PRIMARY KEY"]
   [:created "TIMESTAMP NOT NULL"]
   [:mime_type "CHARACTER VARYING(255) NOT NULL"]
   [:file_name "CHARACTER VARYING(255) NOT NULL"]
   [:file_data "BYTEA NOT NULL"]])

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
  "create files table and return true. Throws if error."
  [db]
  (let [sql-command (sql/create-table-ddl :files table-ddl)]
    (sql/db-do-commands db [sql-command])))

(defn drop-files-table
  "drop files table and return true. Throws if error."
  [db]
  (let [sql-command (sql/drop-table-ddl :files)]
    (sql/db-do-commands db [sql-command])))

(defn create-file
  "inserts file in db and return map of created file. Throws if error."
  [db file-name mime-type file-data]
  (first (sql/insert! db :files {:id (uuid)
                                 :created (timestamp)
                                 :file_name file-name
                                 :mime_type mime-type
                                 :file_data file-data})))
(defn delete-file
  "deletes file in db. Throws if error."
  [db id]
  (if (valid-id? id)
    (sql/execute! db ["DELETE FROM files WHERE id = ?" id])
    (throw (Exception. "invalid file id"))))

(defn get-files
  "returns list of files in db up to limit. Throws if error."
  [db limit]
  (if (pos-int? limit)
    (sql/query db ["SELECT * FROM files ORDER BY created DESC LIMIT ?" limit])
    (throw (Exception. "invalid parameter limit"))))

(defn get-file
  "return file as map from db or nil if not found. Throws if error."
  [db id]
  (if (valid-id? id)
    (let [result (sql/query db ["SELECT * FROM files WHERE id = ?" id])]
      (if (nil? result) nil
          (first result)))
    (throw (Exception. "invalid file id"))))

(comment
  (db-connection? (clojure.edn/read-string (slurp "resources/db.edn")))
  (get-file (clojure.edn/read-string (slurp "resources/db.edn")) "moi" )
  (create-files-table (clojure.edn/read-string (slurp "resources/db.edn")))
  (drop-files-table (clojure.edn/read-string (slurp "resources/db.edn")))
  (get-files (clojure.edn/read-string (slurp "resources/db.edn")) 10 )
  )
