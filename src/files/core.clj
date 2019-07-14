(ns files.core
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes DELETE GET POST routes]]
            [compojure.route :as route]
            [files.db :as db]
            [jdbc.pool.c3p0 :as pool]
            [files.handlers :as h]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.basic-authentication
             :refer
             [wrap-basic-authentication]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]])
  (:import org.eclipse.jetty.server.handler.StatisticsHandler))

(defn error-exit
  "print error message and exit"
  [err]
  (log/error (str "Error:" (if (instance? Exception err) (.getMessage err) err)))
  (System/exit 1))

;; read main configuration file
(def config (try (clojure.edn/read-string (slurp "config.edn"))
                 (catch Exception e (error-exit e))))

;; set up db configuration with connection pooling
(def db-spec (pool/make-datasource-spec (:db-pool config)))

(defn access-denied
  "return http 403 response"
  [user]
  {:status 403 :body (str "access denied for user " user) :headers {}})

(defonce http-server (atom nil))

(defn stop
  "gracefully stop the web server"
  []
  (when (some? @http-server)
    (log/info "server shutdown in progress..")
    (future (.stop @http-server)
            (log/info "closing db connections")
            (.close (:datasource db-spec)))
    "Bye"))

(defn authenticate
  "authenticate user and return authenticated user name or nil"
  [username password]
  (when-not (or (nil? username) (nil? password))
    (if (= username (get-in config [:admin :name]))
      (when (= password (get-in config [:admin :password])) username)
      (when (= password (get-in config [:users username])) username))))

(defn wrap-admin-routes
  "permit access only to admin user for routes starting with uri"
  [handler]
  (fn [request]
    (if (= (get-in config [:admin :name]) (:basic-authentication request))
      (handler request)
      (access-denied (:basic-authentication request)))))

(def admin-routes
  (routes
   (GET "/admin" []  (h/index db-spec "/api/files" "/admin/shutdown"))
   (GET "/admin/shutdown" [] (stop))))

;; define routes
(def api-routes
  (routes
   (GET "/api/files/:id" [id] (h/get-file db-spec id))
   (DELETE "/api/files/:id" [id] (h/delete-file db-spec id))
   (GET "/api/files" [limit] (h/get-files-json db-spec limit))
   (POST "/api/files" request (h/create-file db-spec request))))

(def pub-routes
  (routes
   (GET "/swagger" [] (clojure.java.io/resource "public/index.html"))
   (GET "/" [] (clojure.java.io/resource "public/index.html"))
   (route/resources "/")))

(def app
  (routes
   pub-routes
   (-> (routes api-routes (wrap-admin-routes admin-routes))
       (wrap-basic-authentication authenticate)
       wrap-params
       wrap-multipart-params)
   (route/not-found "Nothing")))

(defn configurator
  "return configurator for jetty"
  [server]
  (let [stats-handler (StatisticsHandler.)]
    (.setHandler stats-handler (.getHandler server))
    (.setHandler server stats-handler)
    (.setStopTimeout server (* 1000 (:shutdown-secs config)))
    (.setStopAtShutdown server true)))

(defn start
  "start application"
  []
  (if (db/db-connection? db-spec)
    (try
      (when-not (db/files-exists? db-spec)
        (log/info "DB connection ok, but files table missing. Creating files table.")
        (db/create-files-table db-spec))
      (if (db/files-exists? db-spec)
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
