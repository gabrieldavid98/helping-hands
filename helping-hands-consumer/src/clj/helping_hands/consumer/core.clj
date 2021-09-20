(ns helping-hands.consumer.core
  "Initialize Helping Hands Consumer Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.consumer.persistence :as p]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private consumerdb
  (delay (p/create-consumer-database "consumer")))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))]
    (if (and (seq params)
             (or (params :id) (params :mobile) (params :email) (params :address)))
      (let [flds (if-let [fl (:flds params)]
                   (map s/trim (s/split fl #","))
                   (vector))
            params (assoc params :flds flds)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context :response {:status 400
                                 :body (str "One of Address, email, and "
                                            "mobile is mandatory")})))))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if-let [id (or (-> context :request :form-params :id)
                            (-> context :request :query-params :id)
                            (-> context :request :path-params :id))]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid Consumer ID"}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if-let [params (-> context :request :form-params)]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid parameters"}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

(def get-consumer
  {:name ::consumer-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  entity (.entity @consumerdb (:id tx-data) (:flds tx-data))]
              (if (nil? (:db/id entity))
                (assoc context :response {:status 404
                                          :body "No such consumer"})
                (assoc context :response {:status 200
                                          :body (jp/generate-string entity)}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

(def upsert-consumer
  {:name ::consumer-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (:id tx-data)
                  db (.upsert @consumerdb id (:name tx-data)
                              (:address tx-data) (:mobile tx-data)
                              (:email tx-data) (:geo tx-data))]
              (if (nil? db)
                (throw (IOException.
                        (str "Upsert failed for consumer: " id)))
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @consumerdb id []))}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

(def create-consumer
  {:name ::consumer-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (str (UUID/randomUUID))
                  tx-data (if (:id tx-data) tx-data (assoc tx-data :id id))
                  db (.upsert @consumerdb id (:name tx-data)
                              (:address tx-data) (:mobile tx-data)
                              (:email tx-data) (:geo tx-data))]
              (if (nil? db)
                (throw (IOException.
                        (str "Upsert failed for consumer: " id)))
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @consumerdb id []))}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})

(def delete-consumer
  {:name ::consumer-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  db (.delete @consumerdb (:id tx-data))]
              (if (nil? db)
                (assoc context :response {:status 404 :body "No such consumer"})
                (assoc context :respose {:status 200 :body "Success"}))))
   :error (fn [context ex-info]
            (assoc context
                   :response {:status 500
                              :body (.getMessage ex-info)}))})
