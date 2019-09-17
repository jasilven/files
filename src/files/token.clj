(ns files.token
  (:import [java.time Instant])
  (:require  [buddy.sign.jwt :as jwt]))

(def sign-opts {:alg :hs512})

(defn validate
  "Validates and and returns token claims, throws if token invalid or on error."
  [token secret]
  (when (or (nil? token))
    (throw (Exception. "Cannot validate, token is nil.")))
  (when (or (nil? secret))
    (throw (Exception. "Cannot validate, secret is nil.")))
  (jwt/unsign token secret sign-opts))

(defn generate
  "Returns user or admin token."
  [{:keys [user origin secret days admin] :or {days 1 admin false origin "unknown"}}]
  (let [expires (.plusSeconds (Instant/now) (* days 24 60 60))]
    (if admin
      (jwt/sign {:admin true :user user :origin origin :exp expires} secret sign-opts)
      (jwt/sign {:user user :origin origin :exp expires} secret sign-opts))))
