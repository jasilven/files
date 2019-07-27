(ns files.core
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes DELETE GET PUT POST routes]]
            [compojure.route :as route]
            [files.db :as db]
            [files.handlers :as h]
            [files.admin-handlers :as admin]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.basic-authentication
             :refer
             [wrap-basic-authentication]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]])
  (:import org.eclipse.jetty.server.handler.StatisticsHandler))

(defn error-exit
  "Print error message and exit."
  [err]
  (log/error (str "Error:" (if (instance? Exception err) (.getMessage err) err)))
  (System/exit 1))

;; read main configuration file
(def config (try (read-string (slurp "config.edn"))
                 (catch Exception e (error-exit e))))

;; set up db configuration with connection pooling
(def ds (try (db/get-datasource config) (catch Exception e (error-exit e))))

(def access-denied {:status 403 :body "access denied" :headers {}})

(defonce http-server (atom nil))

(defn stop
  "Gracefully shutdown the web server."
  []
  (when (some? @http-server)
    (log/info "server shutdown in progress..")
    (future (.stop @http-server)
            (log/info "closing db connections")
            (.close ds))
    "Bye"))

(defn authenticate
  "Authenticate user and return authenticated user name or nil."
  [username password]
  (when-not (or (nil? username) (nil? password))
    (if (= username (get-in config [:admin :name]))
      (when (= password (get-in config [:admin :password])) username)
      (when (= password (get-in config [:users username])) username))))

(defn admin? [request]
  (= (get-in config [:admin :name]) (:basic-authentication request)))

(def admin-routes
  (routes
   (GET "/admin" request (if (admin? request) (admin/main ds config) access-denied))
   (POST "/admin/upload" request (if (admin? request) (admin/upload ds request) access-denied))
   (GET "/admin/delete/:id" [id :as request] (if (admin? request) (admin/delete ds id) access-denied))
   (GET "/admin/undelete/:id" [id :as request] (if (admin? request) (admin/undelete ds id) access-denied))
   (GET "/admin/download/:id" [id :as request] (if (admin? request) (admin/download ds id) access-denied))
   (GET "/admin/details/:id" [id :as request] (if (admin? request) (admin/details ds id) access-denied))
   (GET "/admin/shutdown" request (if (admin? request) (stop) access-denied))))

(def api-routes
  (routes
   (GET "/api/files/:id" [id] (h/get-document ds id true))
   (PUT "/api/files/:id" [id :as request] (h/update-document ds id request))
   (DELETE "/api/files/:id" [id] (h/delete-document ds id))
   (GET "/api/files" [limit] (h/get-documents-json ds limit))
   (POST "/api/files" request (h/create-document ds request))))

(def pub-routes
  (routes
   (GET "/swagger" [] (clojure.java.io/resource "public/index.html"))
   (GET "/" [] (clojure.java.io/resource "public/index.html"))
   (route/resources "/")))

(def app
  (routes
   pub-routes
   (-> (routes api-routes admin-routes)
       (wrap-basic-authentication authenticate)
       wrap-params
       wrap-multipart-params)
   (route/not-found "Nothing")))

(defn configurator
  "Return configurator for jetty."
  [server]
  (let [stats-handler (StatisticsHandler.)]
    (.setHandler stats-handler (.getHandler server))
    (.setHandler server stats-handler)
    (.setStopTimeout server (* 1000 (:shutdown-secs config)))
    (.setStopAtShutdown server true)))

(defn start
  "Start application."
  []
  (if (db/db-connection? ds)
    (try
      (when-not (db/files-exists? ds)
        (log/info "DB connection ok, but files table missing. Creating files table.")
        (db/create-files-table ds))
      (if (db/files-exists? ds)
        (reset! http-server (run-jetty app (assoc (:jetty config)
                                                  :configurator configurator)))
        (error-exit "Problem with creating files table. Exiting."))
      (log/info "Server started")
      (catch Exception e (error-exit e)))
    (error-exit "No DB connection. Ensure that DB is running or check configuration.")))

(comment
  (start)
  (stop))

(defn -main [] (start))
