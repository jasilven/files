(ns files.db
  (:require [clojure.java.jdbc :as sql]))

(def table-ddl
  [[:id "CHARACTER VARYING(255) PRIMARY KEY"]
   [:created "TIMESTAMP NOT NULL"]
   [:mime_type "CHARACTER VARYING(255) NOT NULL"]
   [:file_name "CHARACTER VARYING(255) NOT NULL"]
   [:file_data "BYTEA NOT NULL"]])

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn timestamp [] (java.sql.Timestamp. (.getTime (java.util.Date.))))

(defn db-connection? [db]
  (try (sql/query db ["SELECT COUNT(1+1)"]) true
       (catch Exception e false)))

(defn files-exists? [db]
  (try (sql/query db ["SELECT COUNT (*) FROM files"]) true
       (catch Exception e false)))

(defn create-files-table [db]
  (let [sql-command (sql/create-table-ddl :files table-ddl)]
    (sql/db-do-commands db [sql-command])))

(defn drop-files-table [db]
  (let [sql-command (sql/drop-table-ddl :files)]
    (sql/db-do-commands db [sql-command])))

(defn create-file [db file-name mime-type file-data]
  (sql/insert! db :files {:id (uuid)
                          :created (timestamp)
                          :file_name file-name
                          :mime_type mime-type
                          :file_data file-data}))
(defn delete-file [db id]
  (sql/execute! db ["DELETE FROM files WHERE id = ?" id]))

(defn get-files [db limit]
  (sql/query db ["SELECT * FROM files ORDER BY created DESC LIMIT ?" limit]))

(defn get-file [db id]
  (sql/query db ["SELECT * FROM files WHERE id = ?" id]))
