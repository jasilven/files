(ns files.core
  (:gen-class)
  (:require [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [routes GET POST DELETE]]
            [compojure.route :as route]
            [clojure.pprint]
            [files.db :as db]
            [files.handlers :as h]))

(defonce port 8080)

(defonce db (clojure.edn/read-string (slurp "resources/db.edn")))

(defonce http-server (atom nil))

(def app-routes
  (routes
   (GET "/" [] (h/index db "/files"))
   (GET "/files/:id" [id] (h/get-file db id))
   (DELETE "/files/:id" [id] (h/delete-file db id))
   (GET "/files" [limit] (h/get-files db limit true))
   (POST "/files" request (h/create-file db request))
   (route/not-found "Page not found")))

(def app (-> app-routes wrap-params wrap-multipart-params))

(defn stop []
  (when (some? @http-server) (.stop @http-server)))

(defn start []
  (stop)
  (if (db/db-connection? db)
    (do
      (when-not (db/files-exists? db) (db/create-files-table db))
      (if (db/files-exists? db)
        (do
          (reset! http-server (jetty/run-jetty app {:port port :join? false}))
          (println "Server running at" (str "http://localhost:" port)))
        (println "Database connection ok, but files table missing and unable to create it!")))
    (do
      (println "No database connection!")
      (clojure.pprint/pprint (assoc db :password "**********")))))

(comment
  (start)
  (stop))

(defn -main [] (start))
