(ns helping-hands.consumer.core
  "Initialize Helping Hands Consumer Service"
  (:require [clojure.string :as s]
            [helping-hands.consumer.persistence :as p]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.consumer.http :refer [json]])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private consumerdb
  (delay (p/create-consumer-database)))

(defn error-handler'
  "Handles interceptor errors"
  [context ex-info]
  (json context :internal-server-error (.getMessage ex-info)))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))]
    (if (and (seq params)
             (or (params :id)
                 (params :mobile)
                 (params :email)
                 (params :address)))
      (let [fields (if-let [fl (:fields params)]
                   (map s/trim (s/split fl #","))
                   (vector))
            params (assoc params :fields fields)]
        (assoc context :tx-data params))
      (chain/terminate
       (json context :bad-request (str "One of Address, email, and "
                                       "mobile is mandatory"))))))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if (or (-> context :request :form-params :id)
                    (-> context :request :query-params :id)
                    (-> context :request :path-params :id))
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid Consumer ID"))))
   :error error-handler'})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid parameters"))))
   :error error-handler'})

(def get-consumer
  {:name ::consumer-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  entity (.entity @consumerdb id (:fields tx-data))]
              (if (empty? entity)
                (json context :not-found "No such consumer")
                (json context :ok entity))))
   :error error-handler'})

(def upsert-consumer
  {:name ::consumer-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  db (.upsert @consumerdb id (:name tx-data)
                              (:address tx-data) (:mobile tx-data)
                              (:email tx-data) (:geo tx-data))]
              (if (nil? db)
                (throw (IOException.
                        (str "Upsert failed for consumer: " id)))
                (json context :ok (.entity @consumerdb id [])))))
   :error error-handler'})

(def create-consumer
  {:name ::consumer-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/randomUUID)
                  db (.upsert @consumerdb id (:name tx-data)
                              (:address tx-data) (:mobile tx-data)
                              (:email tx-data) (:geo tx-data))]
              (if (nil? db)
                (throw (IOException.
                        (str "Upsert failed for consumer: " id)))
                (json context :created (.entity @consumerdb id [])))))
   :error error-handler'})

(def delete-consumer
  {:name ::consumer-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db (.delete @consumerdb (:id tx-data))]
              (if (nil? db)
                (json context :not-found "No such consumer")
                (json context :ok "Success"))))
   :error error-handler'})
