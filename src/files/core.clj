(ns files.core
  (:gen-class)
  (:import [org.eclipse.jetty.server.handler StatisticsHandler])
  (:require [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.adapter.jetty :as jetty]
            [clojure.core.async :as async]
            [compojure.core :refer [routes GET POST DELETE]]
            [compojure.route :as route]
            [clojure.pprint]
            [files.db :as db]
            [files.handlers :as h]))

(defonce port 8080)
(defonce db (clojure.edn/read-string (slurp "resources/db.edn")))
(defonce http-server (atom nil))

(defn stop []
  (when (some? @http-server)
    (async/go (.stop @http-server))
    (println "Server shutdown")
    "Server shutdown"))

(def app-routes
  (routes
   (GET "/" [] (h/index db "/files"))
   (GET "/shutdown" [] (stop))
   (GET "/files/:id" [id] (h/get-file db id))
   (DELETE "/files/:id" [id] (h/delete-file db id))
   (GET "/files" [limit] (h/get-files db limit true))
   (POST "/files" request (h/create-file db request))
   (route/not-found "Page not found")))

(def app (-> app-routes wrap-params wrap-multipart-params))

(defn conf
  [server]
  (let [stats-handler (StatisticsHandler.)
        default-handler (.getHandler server)]
    (.setHandler stats-handler default-handler)
    (.setHandler server stats-handler)
    (.setStopTimeout server 5000)
    (.setStopAtShutdown server true)))

(defn start []
  (stop)
  (if (db/db-connection? db)
    (do
      (when-not (db/files-exists? db) (db/create-files-table db))
      (if (db/files-exists? db)
        (do
          (reset! http-server (jetty/run-jetty app {:port port :join? false :configurator conf}))
          (println "Server started at" (str "http://localhost:" port)))
        (println "Database connection ok, but files table missing and unable to create it!")))
    (do
      (println "No database connection!")
      (clojure.pprint/pprint (assoc db :password "**********")))))

(comment
  (start)
  (stop))

(defn -main [] (start))
