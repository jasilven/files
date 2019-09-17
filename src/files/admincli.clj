(ns files.admincli
  (:gen-class)
  (:import [java.time Instant])
  (:require [cli-matic.core :refer [run-cmd]]
            [clj-http.client :as client]
            [files.token :as token]))

(def uri "https://localhost:8080")

(defn shutdown
  "Shutdown the server!"
  [{:keys [secret]}]
  (try
    (let [token (token/generate {:user "admincli" :origin "commandline" :days 1 :secret secret :admin true})
          auth-options {:insecure? true :oauth-token token}]
      (client/get (str uri "/admin/shutdown") auth-options)
      (println "Server shutting down."))
    (catch Exception e (println "Error:" (.getMessage e)))))

(defn generate-token
  "Generates and prints out token."
  [claims]
  (try
    (let [claims (if (= (:admin claims) "yes")
                   (assoc claims :admin true)
                   (dissoc claims :admin))
          token (token/generate claims)
          validated (token/validate token (:secret claims))]
      (println "Validated:" validated)
      (println "Expires:" (str (Instant/ofEpochSecond (:exp validated))))
      (println "Token:" token))
    (catch Exception e (println "Error:" (.getMessage e)))))

(defn validate-token
  "Validates and prints out token."
  [{:keys [token secret]}]
  (let [claims (token/validate token secret)]
    (println "Claims:" claims)
    (println "Expires:" (str (Instant/ofEpochSecond (:exp claims))))))

(def CONFIG
  {:app {:command "admtool"
         :description "Admin tool for files API"
         :version "0.1"}
   :commands [{:command "shutdown"
               :description "Validates and prints claims inside JWT token"
               :opts [{:option "secret" :short "s" :as "Secret used for token" :type :string :default :present}]
               :runs shutdown}
              {:command "validate"
               :description "Validates and prints claims inside JWT token"
               :opts [{:option "token" :short "t" :as "JWT token" :type :string :default :present}
                      {:option "secret" :short "s" :as "Secret used for unsigning the token" :type :string :default :present}]
               :runs validate-token}
              {:command "token"
               :description "Generates and prints the JWT token for Files API"
               :opts [{:option "user" :short "u" :as "Userid" :type :string :default :present}
                      {:option "origin" :short "o" :as "Origin" :type :string :default :present}
                      {:option "admin" :short "a" :as "For admin token set this to 'yes'" :type :string :default "no"}
                      {:option "days" :short "d" :as "Number of days this token is valid from now" :type :int :default :present}
                      {:option "secret" :short "s" :as "Secret used for signing the token" :type :string :default :present}]
               :runs generate-token}]})

(defn -main
  [& args]
  (run-cmd args CONFIG))
