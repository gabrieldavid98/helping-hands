(ns helping-hands.auth.core
  "Initialize Helping Hands Consumer Service"
  (:require [clojure.string :as s]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.auth.http :refer [json]]
            [helping-hands.auth.state :refer [auth-db]]
            [helping-hands.auth.jwt :as jwt]
            [bcrypt-clj.auth :as a])
  (:import [java.io IOException]
           [java.util UUID]
           [com.nimbusds.jwt.proc BadJWTException]
           [java.text ParseException]))

(defn error-handler'
  "Handles interceptor errors"
  [context ex-info]
  (json context :internal-server-error (.getMessage ex-info)))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :headers)
                      (when-let [pparams (-> context :request :path-params)]
                        (if (empty? pparams) {} pparams)))]
    (if (or (and (:uid params) (:pwd params))
            (params "authorization"))
      (assoc context :tx-data params)
      (chain/terminate
       (json context :bad-request "Invalid Creds/Token")))))

(defn- extract-token
  "Extracts user and roles map from the auth header"
  [auth]
  (-> (jwt/read-token (second (s/split auth #"\s+")) (:secret auth-db))
      (select-keys ["user" "roles"])))

(def validate
  {:name ::validate
   :enter (fn [context]
            (prepare-valid-context context))
   :error error-handler'})

(def get-token
  {:name ::token-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  uid (:uid tx-data)
                  pwd (:pwd tx-data)
                  auth (tx-data "authorization")]
              (cond
                (and uid pwd (a/check-password
                              pwd
                              (-> auth-db :users (get uid) :pwd)))
                (let [token (jwt/create-token 
                             {:roles (-> auth-db :users (get uid) :roles)
                              :user uid}
                             (:secret auth-db))]
                  (json context :ok {:token token}))
                (and auth (= "Bearer" (-> (s/split auth #"\s+") first)))
                (try
                  (json context :ok {:token (extract-token auth)})
                  (catch BadJWTException _
                    (json context :unauthorized "Token expired")))
                :else (json context :unauthorized "Unauthorized"))))
   :error error-handler'})