(ns helping-hands.alert.core
  "Initialize Helping Hands Consumer Service"
  (:require [clojure.string :as s]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.log :as log]
            [helping-hands.alert.http :refer [json]]))

(defn error-handler'
  "Handles interceptor errors"
  [context ex-info]
  (json context :internal-server-error (.getMessage ex-info)))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (-> context :request :form-params)]
    (if-not (and (empty? params)
                 (empty? (:to params))
                 (empty? (:body params)))
      (let [to-val (map s/trim (s/split (:to params) #","))]
        (assoc context :tx-data (assoc params :to to-val)))
      (chain/terminate
       (json context :bad-request "Both 'to' and 'body' are required")))))

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid parameters"))))
   :error error-handler'})

(def send-email
  {:name ::send-mail
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  msg (->> {:from "admin@helpinghands.com"
                            :to (:to tx-data)
                            :cc (:cc tx-data)
                            :subject (:subject tx-data)
                            :body (:body tx-data)}
                           (filter (comp some? val))
                           (into {}))]
              (log/info :email msg)
              (json context :ok "Email Sent")))
   :error error-handler'})

(def send-sms
  {:name ::send-sms
   :enter (fn [context]
            (let [tx-data (:tx-data context)]
              ;;TODO: Send sms
              (log/info :sms tx-data)
              (json context :ok "Success")))
   :error error-handler'})
