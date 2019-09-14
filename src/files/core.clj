(ns files.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes DELETE GET PUT POST routes]]
            [compojure.route :as route]
            [files.db :as db]
            [files.handlers :as h]
            [files.admin-handlers :as admin]
            [ring.adapter.jetty :refer [run-jetty]]
            [buddy.auth.backends :as backends]
            [buddy.auth.accessrules :refer [error wrap-access-rules]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]])
  (:import org.eclipse.jetty.server.handler.StatisticsHandler))

(defn error-exit
  "Print error message and exit."
  [err]
  (log/error (str "Error:" (if (instance? Exception err) (.getMessage err) err)))
  (System/exit 1))

;; main configuration
(def config (try (read-string (slurp "config.edn"))
                 (catch Exception e (error-exit e))))

(def ds (try (db/get-datasource config)
             (catch Exception e (error-exit e))))

(defonce http-server (atom nil))

(defn stop
  "Gracefully shutdown the web server and db connection pool."
  []
  (when (some? @http-server)
    (.start (Thread. (fn []
                       (log/info "Server shutdown in progress.")
                       (.stop @http-server)
                       (log/info "Closing db connections.")
                       (.close ds)
                       (log/info "Server stopped."))))
    "Shutting down"))

(def admin-routes
  (routes
   (GET "/admin" request (admin/main ds config))
   (POST "/admin/upload" request (admin/upload ds request))
   (GET "/admin/close/:id" [id :as request] (admin/close ds id))
   (GET "/admin/open/:id" [id :as request] (admin/open ds id))
   (GET "/admin/download/:id" [id :as request] (admin/download ds id))
   (GET "/admin/details/:id" [id :as request] (admin/details ds id))
   (GET "/admin/shutdown" request (stop))))

(def api-routes
  (routes
   (GET "/api/files/:id" [id] (h/get-document ds id true))
   (PUT "/api/files/:id" [id :as request] (h/update-document ds id request))
   (DELETE "/api/files/:id" [id] (h/close-document ds id))
   (GET "/api/files" [limit] (h/get-documents-json ds limit))
   (POST "/api/files" request (h/create-document ds request))))

(def pub-routes
  (routes
   (GET "/swagger" [] (clojure.java.io/resource "public/index.html"))
   (GET "/" [] (clojure.java.io/resource "public/index.html"))
   (route/resources "/")))

(defn admin?
  [request]
  (if (true? (get-in request [:identity :admin])) true false))

(defn authenticated?
  [request]
  (if-not (nil? (:identity request)) true false))

(defn any-access
  [request]
  true)

(def access-rules [{:pattern #"^/admin/.*" :handler admin?}
                   {:pattern #"^/admin$" :handler admin?}
                   {:pattern #"^/swagger$" :handler any-access}
                   {:pattern #"^/swagger.yaml$" :handler any-access}
                   {:pattern #"^/public/.*$" :handler any-access}
                   {:pattern #"^/css/.*$" :handler any-access}
                   {:pattern #"^/.*" :handler authenticated?}])

(defn auth-error [request exp]
  (log/warn exp "Authentication failed:" (dissoc request :headers) ))

(defn access-error [request exp]
  (log/warn exp "Unauthorized access attempt:" (dissoc request :headers) ))

(def app
  (routes
   pub-routes
   (-> (routes api-routes admin-routes)
       (wrap-access-rules {:rules access-rules
                           :on-error access-error})
       (wrap-authentication (backends/jws {:secret (:secret config)
                                           :on-error auth-error
                                           :options {:alg :hs256}
                                           :token-name "Bearer"}))
       wrap-params
       wrap-multipart-params)
   (route/not-found "Not Found!")))

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
        (reset! http-server (run-jetty app
                                       (assoc (:jetty config) :configurator configurator)))
        (error-exit "Problem with creating files table. Exiting."))
      (log/info "Server started.")
      (catch Exception e (error-exit e)))
    (error-exit "No DB connection. Ensure that DB is running or check configuration.")))

(comment
  (start)
  (stop))

(defn -main []
  (start))

;; (defn authenticate
;;   "Authenticate user and return authenticated user name or nil."
;;   [username password]
;;   (when-not (or (nil? username) (nil? password))
;;     (if (= username (get-in config [:admin :name]))
;;       (when (= password (get-in config [:admin :password])) username)
;;       (when (= password (get-in config [:users username])) username))))

;; (defn authenticated-user
;;   "Return true if user is authenticated"
;;   [request]
;;   (if (:identity request) true false))

;; (defn admin? [request]
;;   (= (get-in config [:admin :name]) (:basic-authentication request)))

;; (defn access-denied [request value]
;;   {:status 401 :body "access denied" :headers {}})
