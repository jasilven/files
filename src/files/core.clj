(ns files.core
  (:gen-class)
  (:require [buddy.auth.accessrules :refer [wrap-access-rules]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [clojure.tools.logging :as log]
            [compojure.core :refer [DELETE GET POST PUT routes]]
            [compojure.route :as route]
            [files.db :as db]
            [files.handlers :as h]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.java.io :as io])
  (:import org.eclipse.jetty.server.handler.StatisticsHandler))

(defonce ^:private CONFIG (atom nil))
(defonce ^:private SERVER (atom nil))

(defn error-exit
  "Print error message and exit with return code 1."
  [err]
  (log/error (str (if (instance? Exception err) (.getMessage err) err)))
  (System/exit 1))

(defn reset-config!
  "Read and initialize main configuration from config file/arguments/environment variables.
  If no ssl-keystore password or secret are passed as arguments they are read from
  environment variables KEYPASS and TOKEN_SECRET."
  [{:keys [path keypass token-secret] :or {keypass (System/getenv "KEYPASS")
                                           token-secret (System/getenv "TOKEN_SECRET")}}]
  (when (nil? token-secret)
    (error-exit "Token secret missing. Set it with TOKEN_SECRET env variable."))
  (reset! CONFIG
          (-> (read-string (slurp path))
              (assoc-in [:jetty :key-password] keypass)
              (assoc-in [:db-spec :user] (System/getenv "DBUSER"))
              (assoc-in [:db-spec :password] (System/getenv "DBPASS"))
              (assoc :token-secret token-secret))))

(defn stop
  "Gracefully shutdown the web server and database/datasource connection pool."
  []
  (try
    (when (some? @SERVER)
      (do
        (log/info "Server shutdown in progress.")
        (.stop @SERVER)
        (reset! SERVER nil)
        (log/info "Closing db connections.")
        (db/drop-datasource)
        (log/info "Server stopped.")
        (h/json-ok {:result "Server going down."}))
      (h/json-ok {:result "No server running."}))
    (catch Exception e (h/json-error 500 e (str "Error: " (.getMessage e))))))

(def admin-routes
  (routes
   #_(POST "/admin/upload" request (admin/upload request))
   #_(GET "/admin/close/:id" [id :as request] (admin/close id))
   #_(GET "/admin/open/:id" [id :as request] (admin/open id))
   #_(GET "/admin/download/:id" [id :as request] (admin/download id))
   #_(GET "/admin/details/:id" [id :as request] (admin/details id))))

(def api-routes
  (routes
   (GET "/api/files/:id/download" [id :as request] (h/download request id))
   (GET "/api/files/:id" [id binary :as request] (h/get-document request id binary))
   (PUT "/api/files/:id" [id :as request] (h/update-document request id))
   (DELETE "/api/files/:id" [id :as request] (h/close-document request id))
   (GET "/api/files" [limit :as request] (h/get-documents-json request limit))
   (POST "/api/files" request (h/create-document request))))

(def pub-routes
  (routes
   (POST "/login" request [] (h/login request (:token-secret @CONFIG)))
   (GET "/swagger" [] (io/resource "public/swagger.html"))
   (GET "/docs" [] (io/resource "public/swagger.html"))
   (GET "/" request (h/index request))
   (route/resources "/")
   #_(route/not-found "Not Found")))

(defn admin?
  [request]
  (if (= :admin (get-in request [:identity :role])) true false))

(defn authenticated?
  [request]
  (if-not (nil? (:identity request)) true false))

(defn public [request] true)

(def access-rules [{:pattern #"^/admin/.*" :handler admin?}
                   {:pattern #"^/admin$" :handler admin?}
                   {:pattern #"^/api/.*$" :handler authenticated?}
                   {:pattern #"^/swagger$" :handler public}
                   {:pattern #"^/swagger.html$" :handler public}
                   {:pattern #"^/swagger.yaml$" :handler public}
                   {:pattern #"^/css/.*$" :handler public}
                   {:pattern #"^/js/.*$" :handler public}
                   {:pattern #"^/login$" :handler public}
                   {:pattern #"^/$" :handler public}])

(def access-denied {:status  200 :headers {} :body "Access Denied!"})

(defn auth-error [request exp]
  (log/warn "Authentication failed:" (dissoc request :headers))
  access-denied)

(defn access-error [request exp]
  (log/warn "Unauthorized access attempt:" (dissoc request :headers))
  access-denied)

(defn configurator
  "Returns jetty configurator."
  [server]
  (let [stats-handler (StatisticsHandler.)]
    (.setHandler stats-handler (.getHandler server))
    (.setHandler server stats-handler)
    (.setStopTimeout server (* 1000 (:shutdown-secs @CONFIG)))
    (.setStopAtShutdown server true)))

(defn run-server
  []
  (-> (routes pub-routes api-routes admin-routes (route/not-found "Not found"))
      wrap-params
      wrap-multipart-params
      (wrap-access-rules {:rules access-rules
                          :on-error access-error})
      (wrap-authentication (backends/jws {:secret (:token-secret @CONFIG)
                                          :on-error auth-error
                                          :options {:alg :hs512}
                                          :token-name "Bearer"}))
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-credentials "true"
                 :access-control-allow-methods [:post :get :put :delete])
      (run-jetty (assoc (:jetty @CONFIG) :configurator configurator))))

(defn start
  "Starts database/datasource connection and web server."
  ([config]
   (try
     (reset-config! config)
     (db/setup-datasource (:db-spec @CONFIG))
     (when-not (db/db-connection?)
       (throw (.Exception "No DB connection. Check DB and/or DB configuration.")))
     (when-not (db/files-exists?)
       (log/info "DB connection ok, but files table missing. Creating files table.")
       (db/create-files-table))
     (reset! SERVER (run-server))
     (log/info "Server started.")
     (catch Exception e (error-exit e)))))

(comment
  (if (nil? @SERVER)
    (start {:path "config_test.edn" :keypass "123456" :token-secret "123456"})
    "Stop the server first")
  (stop)
  ;;
  )

(defn -main []
  (start {:path "config.edn"}))
