(ns files.db
  (:import org.postgresql.util.PGobject)
  (:require [next.jdbc :as jdbc]
            [clojure.set :as set]
            [jdbc.pool.c3p0 :as pool]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set  :as rs]
            [files.json :as json]))

(defonce ^:private DS (atom nil))

(def files-table-create ["CREATE TABLE files (
  id VARCHAR(36) PRIMARY KEY,
  created TIMESTAMP NOT NULL,
  updated TIMESTAMP,
  closed TIMESTAMP,
  mimetype VARCHAR NOT NULL,
  filename VARCHAR NOT NULL,
  filedata TEXT NOT NULL,
  filesize INTEGER NOT NULL,
  category VARCHAR,
  metadata JSON)"])

(def files-table-cols [:id :created :updated :closed :mimetype :filename :filesize :category :metadata])

(def auditlog-table-create ["CREATE TABLE auditlog (
  id SERIAL PRIMARY KEY,
  fileid VARCHAR(36) REFERENCES files(id) NOT NULL,
  created TIMESTAMP NOT NULL,
  event VARCHAR(36) NOT NULL,
  origin VARCHAR(50) NOT NULL,
  userid VARCHAR(50) NOT NULL)"])

(def files-table-drop ["DROP TABLE files, auditlog cascade"])
(def DEFAULT-QUERY-LIMIT 100) ;; default query limit for files query
(def MAX-QUERY-LIMIT 1000)    ;; max query limit for files query
(def next-opts {:builder-fn rs/as-unqualified-maps}) ;; drop namespace

(defn map->pgjson
  "Converts map to postgres JSON object to allow saving it correctly into db"
  [m]
  (let [pgo (PGobject.)]
    (doto pgo
      (.setType "json")
      (.setValue (json/clj->json m)))
    pgo))

;; Conversions from postgres JSON data type to EDN and java.sql.Timestamp to Instant
(extend-protocol rs/ReadableColumn
  PGobject
  (read-column-by-label ^clojure.lang.IPersistentMap [^PGobject pgo _]
    (let [type (.getType pgo)
          value (.getValue pgo)]
      (case type
        "json" (json/json->clj value)
        :else value)))
  (read-column-by-index ^clojure.lang.IPersistentMap [^PGobject pgo _2 _3]
    (let [type (.getType pgo)
          value (.getValue pgo)]
      (case type
        "json" (json/json->clj value)
        :else value)))
  java.sql.Timestamp
  (read-column-by-label ^java.time.Instant [^java.sql.Timestamp v _]
    (.toInstant v))
  (read-column-by-index ^java.time.Instant [^java.sql.Timestamp v _2 _3]
    (.toInstant v)))

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

(defn setup-datasource
  "Initialize pooled datasource."
  [db-spec]
  (try
    (reset! DS (:datasource (pool/make-datasource-spec db-spec)))
    (catch Exception e (.getMessage e))))

(defn drop-datasource
  "Shutdown/close datasource"
  []
  (when-not (nil? @DS)
    (.close @DS)
    (reset! DS nil)))

(defn db-connection?
  "Datasource connection test. Return true if db connection exists else false."
  []
  (try (jdbc/execute-one! @DS ["SELECT COUNT(1+1)"] next-opts) true
       (catch Exception e false)))

(defn files-exists?
  "Return true if files table exists or else false."
  []
  (try (jdbc/execute-one! @DS ["SELECT COUNT (*) FROM files"] next-opts) true
       (catch Exception e false)))

(defn create-files-table
  "Create files table and auditlog table. Throws if error."
  []
  (jdbc/with-transaction [tx @DS]
    (jdbc/execute! tx files-table-create)
    (jdbc/execute! tx auditlog-table-create)))

(defn drop-files-table
  "Drop files table and return true. Throws if error."
  []
  (jdbc/execute! @DS files-table-drop))

(defn auditlog!
  "Write audit log entry {:fileid :created :userid :origin :event}. Throws if error.
  Note: in postgres 'user' is reserved word, so it needs to be renamed like here to userid."
  [tx entry]
  (let [auditentry (select-keys (set/rename-keys entry {:user :userid})
                                [:userid :origin :fileid :created :event])]
    (sql/insert! tx :auditlog auditentry next-opts)))

(defn create-document
  "Create document and return id of created document as map. Throws if error."
  [document identity]
  (let [ts (timestamp)
        doc (assoc document
                   :id (uuid)
                   :metadata (map->pgjson (:metadata document))
                   :created ts
                   :filesize (.length (:filedata document)))]
    (jdbc/with-transaction [tx @DS]
      (let [result (sql/insert! tx :files doc (merge {:return-keys ["id"]} next-opts))]
        (auditlog! tx (merge identity {:fileid (:id doc) :created ts :event "create"}))
        result))))

(defn update-document
  "Update document and return true on success. Throws if error."
  [id document identity]
  (let [ts (timestamp)
        doc (assoc (dissoc document :id :created :closed)
                   :metadata (map->pgjson (:metadata document))
                   :updated ts
                   :filesize (.length (:filedata document)))]
    (jdbc/with-transaction [tx @DS]
      (let [result (sql/update! tx :files doc {:id id} next-opts)]
        (when (zero? (:next.jdbc/update-count result))
          (throw (ex-info "document update failed, maybe non-existing document" {:id id :document document})))
        (auditlog! tx (merge identity {:fileid id :created ts :userid "unknown" :event "update"}))
        true))))

(defn close-document
  "Mark document closed with timestamp and return true on success and false
  if document already closed. Throws if error."
  [id identity]
  (let [doc (jdbc/execute-one! @DS ["SELECT closed FROM files WHERE id = ?" id] next-opts)]
    (when (nil? doc) (throw (ex-info "cannot close, document not found" {:id id})))
    (if (nil? (:closed doc))
      (jdbc/with-transaction [tx @DS]
        (let [ts (timestamp)
              result (sql/update! tx :files {:closed ts} {:id id} next-opts)]
          (when (zero? (:next.jdbc/update-count result))
            (throw (ex-info "document close failed" {:id id :document doc})))
          (auditlog! tx (merge identity {:fileid id :created ts :userid "unknown" :event "close"}))
          true))
      false)))

(defn open-document
  "Open document by setting closed timestamp nil. Return true on success and false
  if document already open. Throws if error."
  [id identity]
  (let [doc (jdbc/execute-one! @DS ["SELECT closed FROM files WHERE id = ?" id] next-opts)]
    (when (nil? doc) (throw (ex-info "cannot open, document not found" {:id id})))
    (if-not (nil? (:closed doc))
      (jdbc/with-transaction [tx @DS]
        (let [ts (timestamp)
              result (sql/update! tx :files {:closed nil} {:id id} next-opts)]
          (when (zero? (:next.jdbc/update-count result))
            (throw (ex-info "document open failed" {:id id :document doc})))
          (auditlog! tx (merge identity {:fileid id :created ts :userid "unknown" :event "open"}))
          true))
      false)))

(defn cols-except
  "Returns columns as ','-separated string where columns in 'except' set are filtered off."
  [cols except]
  (apply str (interpose \, (map #(name %) (remove except files-table-cols)))))

(defn get-documents
  "Return vector of documents (excluding file data) up to limit or
  up to MAX-QUERY-LIMIT if limit is greater than MAX-QUERY-LIMIT. Throws if error."
  ([identity] (get-documents DEFAULT-QUERY-LIMIT identity))
  ([limit identity]
   (let [cols (cols-except files-table-cols #{:filedata})]
     (if (pos-int? limit)
       (->> (sql/query @DS [(str "SELECT " cols " FROM files ORDER BY created DESC LIMIT ?")
                            (min limit MAX-QUERY-LIMIT)] next-opts)
            (mapv #(assoc % :metadata (json/json->clj (:metadata %)))))
       (throw (ex-info "invalid parameter value for limit" {:limit limit}))))))

(defn get-document
  "Return document as map or nil if not found. File data included if binary? is true. Throws if error."
  [id binary? identity]
  (jdbc/with-transaction [tx @DS]
    (let [ts (timestamp)
          cols (cols-except files-table-cols #{:filedata})
          result (if binary?
                   (sql/get-by-id tx :files id next-opts)
                   (jdbc/execute-one! tx [(str "SELECT " cols " FROM files WHERE id = ?") id] next-opts))]
      (when-not (nil? result)
        (auditlog! tx (merge identity {:fileid id :created ts :userid "unknown" :event "read"}))
        (assoc result :metadata (json/json->clj (:metadata result)))))))

(defn get-auditlogs
  "Return vector of log entries up to limit or up to MAX-QUERY-LIMIT
  if limit is greater than MAX-QUERY-LIMIT. Throws if error."
  ([fileid] (get-auditlogs fileid DEFAULT-QUERY-LIMIT))
  ([fileid limit]
   (if (pos-int? limit)
     (sql/query @DS ["SELECT * FROM auditlog WHERE fileid = ? ORDER BY created DESC LIMIT ?"
                     fileid
                     (min limit MAX-QUERY-LIMIT)] next-opts)
     (throw (ex-info "invalid parameter value for limit" {:limit limit})))))
