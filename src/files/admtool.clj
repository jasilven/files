(ns files.admtool
  (:require  [buddy.sign.jwt :as jwt]
             [files.secure :as sec]
             [cli-matic.core :refer [run-cmd]]))

(defn validate-token [{:keys [token secret]}]
  (try
    (println "Claims:" (jwt/unsign token secret {:alg :hs256}))
    (catch Exception e (println (str "Error: " (.getMessage e))))))

(defn generate-token [{:keys [user origin secret]}]
  (let [token (jwt/sign {:user user :origin origin} secret {:alg :hs256})]
    (println "Token:" token)
    (validate-token {:token token :secret secret})))

(defn generate-hash [{:keys [password]}]
  (let [hash (sec/generate-hash password)]
    (println "Hash:" hash)
    (println "Verified:" (sec/check-hash password hash))))

(def CONFIG
  {:app {:command "admtool"
         :description "Admin tool for files API"
         :version "0.1"}
   :commands [{:command "token"
               :description "Generates and prints the JWT token for Files API usage"
               :opts [{:option "user" :short "u" :as "Userid" :type :string :default :present}
                      {:option "origin" :short "o" :as "Origin/Source system related to userid" :type :string :default :present}
                      {:option "secret" :short "s" :as "Secret used for signing the token" :type :string :default :present}]
               :runs generate-token}
              {:command "validate"
               :description "Validates and prints claims inside JWT token"
               :opts [{:option "token" :short "t" :as "JWT token" :type :string :default :present}
                      {:option "secret" :short "s" :as "Secret used for unsigning the token" :type :string :default :present}]
               :runs validate-token}
              {:command "pwhash"
               :description "Generates hash for password to be used in Files API config file"
               :opts [{:option "password" :short "p" :as "Password in clear text format" :type :string :default :present}]
               :runs generate-hash}]})

(defn -main
  [& args]
  (run-cmd args CONFIG))
