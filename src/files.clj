(ns files
  (:gen-class)
  (:require [compojure.core :refer [defroutes GET POST]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.adapter.jetty :as jetty]
            [compojure.route :as route]
            [hiccup.core :refer [html]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as sql]
            [clojure.java.io :as io]))

(def path "/files")
(def download "/download")

(def db {:dbtype "postgresql"
         :dbname "documents"
         :host "localhost"
         :port 5432
         :user "gqf5100"
         :password "postgres"})

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn save-file [name mime-type fname]
  (let [result (sql/insert! db :files {:id (uuid)
                                       :created (java.sql.Timestamp. (.getTime (java.util.Date.)))
                                       :file_name name
                                       :mime_type mime-type
                                       :file_data (file->bytes fname)})]
    (select-keys (first result) [:id :created :file_name :mime_type])))

(defn json-response [status data]
  {:status status
   :body (json/write-str data)
   :headers {"Content-Type" "application/json"}})

(defn get-files [limit to-json]
  (let [limit (if (nil? limit) 100 limit)
        result (sql/query db ["SELECT id, created, file_name, mime_type FROM files ORDER BY created DESC LIMIT ?" limit])]
    (if to-json
      (json-response 200 result)
      result)))

(defn get-file [id to-json]
  (let [result (sql/query db ["SELECT id, created, file_name, mime_type FROM files WHERE id = ?" id])]
    (if to-json
      (json-response 200 result)
      result)))

(defn download-file [id]
  (let [result (first (sql/query db ["SELECT file_data, mime_type FROM files WHERE id = ?" id]))]
    {:status 200
     :headers {"Content-type" (:mime_type result)}
     :body (io/input-stream (:file_data result))}))

(extend-type java.sql.Timestamp
  json/JSONWriter
  (-write [date out]
    (json/-write (str date) out)))

(defn response [status & elements]
  {:status status
   :body (html elements)
   :headers {"Content-Type" "text/html"}})

(def ok (partial response 200))

(defn index [request]
  (ok [:html [:meta {:charset "UTF-8"}]
       [:body
        [:h3 "Database"]
        [:pre (:dbtype db) " at " (:host db) ":" (:port db) " using database " (:dbname db)]
        [:h3 "Upload"]
        [:form {:action path :method "post" :enctype "multipart/form-data"}
         [:input {:name "file" :type "file" :size "40"}]
         [:input {:type "submit" :name "submit" :value "submit"}]]
        [:h3 "Files"]
        [:ul (for [file (get-files 50 false)]
               [:li
                [:a {:href (str path "/" (:id file))} (:file_name file)] " "
                (:created file) " "
                [:i (:mime_type file)] " "
                [:a {:href (str download "/" (:id file))} [:b "download"]]])]]]))

(defn upload-file [request]
  (let [file-param (get (:params request) "file")
        response (save-file (:filename file-param) (:content-type file-param) (:tempfile file-param))]
    (json-response 200 response)))

(defroutes handler
  (GET "/" [] index)
  (GET (str path "/:id") [id] (get-file id true))
  (GET  "/download/:id" [id] (download-file id))
  (GET path [limit] (get-files limit true))
  (POST path [] upload-file)
  (route/not-found (ok [:h1 "Page not found"])))

(defonce http-server (atom nil))

(def app (-> handler wrap-params wrap-multipart-params))

(defn stop []
  (when (some? @http-server) (.stop @http-server)))

(defn start []
  (let [port 8080]
    (stop)
    (reset! http-server (jetty/run-jetty app {:port port :join? false}))
    (println "Server running at" (str "http://localhost:" port))))

(defn restart []
  (stop)
  (start))

(comment
  (restart)
  (stop))

(defn -main []
  (start))
