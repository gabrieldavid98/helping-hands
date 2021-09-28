(ns helping-hands.order.core
  (:require [clojure.string :as s]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.order.persistence :as p]
            [helping-hands.order.http :refer [json]])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private orderdb
  (delay (p/create-order-database)))

(defn error-handler'
  "Handles interceptor errors"
  [context ex-info]
  (json context :internal-server-error (.getMessage ex-info)))

(defn- validate-rating-cost-ts
  "Validate the rating, cost and start/end timestamp"
  [context]
  (let [rating (-> context :request :form-params :rating)
        cost (-> context :request :form-params :cost)
        start (-> context :request :form-params :start)
        end (-> context :request :form-params :end)]
    (try
      (let [context (if-not (nil? rating)
                      (assoc-in context [:request :form-params :rating]
                                (Double/parseDouble rating))
                      context)
            context (if-not (nil? cost)
                      (assoc-in context [:request :form-params :cost]
                                (Double/parseDouble cost))
                      context)
            context (if-not (nil? start)
                      (assoc-in context [:request :form-params :start]
                                (Long/parseLong start))
                      context)
            context (if-not (nil? end)
                      (assoc-in context [:request :form-params :end]
                                (Long/parseLong end))
                      context)]
        context)
      (catch Exception _ nil))))

(defn- service-exists?
  "Validates the service via Service APIs"
  [_service]
  true)

(defn- provider-exists?
  "Validates the provider via Provider Service"
  [_provider]
  true)

(defn- consumer-exists?
  "Validates the consumer via Consumer Service"
  [_consumer]
  true)

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx (validate-rating-cost-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :reuest :form-params :rating)
                        :cost (-> ctx :request :form-params :cost)
                        :start (-> ctx :request :form-params :start)
                        :end (-> ctx :request :form-params :end))
                 params)]
    (if (and (seq params)
             (not (nil? ctx))
             (:id params)
             (:service params)
             (:provider params)
             (:consumer params)
             (:cost params)
             (:status params)
             (contains? #{"O" "I" "D" "C"} (:status params))
             (service-exists? (:service params))
             (provider-exists? (:provider params))
             (consumer-exists? (:consumer params)))
      (let [fields (if-let [fl (:fields params)]
                      (map s/trim (s/split fl #","))
                      (vector))
            params (assoc params :fields fields)]
        (assoc context :tx-data params))
      (chain/terminate
       (json context :bad-request (str "ID, service, provider, consumer, "
                                    "cost and status is mandatory. start/end, "
                                    "rating and cost must be a number "
                                    "with status having one of values O, I, D or C"))))))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if (or (-> context :request :form-params :id)
                    (-> context :request :query-params :id)
                    (-> context :request :path-params :id))
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid Order ID"))))
   :error error-handler'})

(def validate-get-id
  {:name ::validate-get-id
   :enter (fn [context]
            (if (or (-> context :request :form-params :id)
                    (-> context :request :query-params :id)
                    (-> context :request :path-params :id))
              (let [params (merge (-> context :request :form-params)
                                  (-> context :request :query-params)
                                  (-> context :request :path-params))]
                (if (and (seq params)
                         (:id params))
                  (let [fields (if-let [fl (:fields params)]
                                 (map s/trim (s/split fl #","))
                                 (vector))
                        params (assoc params :fields fields)]
                    (assoc context :tx-data params))
                  (chain/terminate
                   (json context :bad-request "Invalid Order ID"))))
              (chain/terminate
               (json context :bad-request "Invalid Order ID"))))
   :error error-handler'})

(def validate-all-orders
  {:name ::validate-all-orders
   :enter (fn [context]
            (if (-> context :tx-data)
              (-> context
                  (assoc-in [:tx-data :fields]
                            (if-let [fl (-> context :request :query-params :fields)]
                              (map s/trim (s/split fl #","))
                              (vector)))
                  (assoc-in [:tx-data :uid] (-> context :request :form-params :uid)))
              (chain/terminate
               (json context :bad-request "Invalid Parameters"))))
   :error error-handler'})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid Parameters"))))
   :error error-handler'})

(def get-order
  {:name ::order-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  entity (.entity @orderdb id (:fields tx-data))]
              (if (empty? entity)
                (json context :not-found "No such order")
                (json context :ok entity))))
   :error error-handler'})

(def get-all-orders
  {:name ::order-get-all
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  uid (UUID/fromString (:uid tx-data))
                  orders (.orders @orderdb uid (:fields tx-data))]
              (if (empty? orders)
                (json context :not-found "No such orders")
                (json context :ok orders))))
   :error error-handler'})

(def upsert-order
  {:name ::order-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  tx (.upsert @orderdb id (:service tx-data)
                              (:provider tx-data) (:consumer tx-data)
                              (:cost tx-data) (:start tx-data) (:end tx-data)
                              (:rating tx-data) (:status tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Upsert failed for order: " id)))
                (json context :ok (.entity @orderdb id [])))))
   :error error-handler'})

(def create-order
  {:name ::order-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/randomUUID)
                  service (UUID/fromString (:service tx-data))
                  provider (UUID/fromString (:provider tx-data))
                  consumer (UUID/fromString (:consumer tx-data))
                  tx (.upsert @orderdb id service provider consumer
                              (:cost tx-data) (:start tx-data) (:end tx-data)
                              (:rating tx-data) (:status tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Create failed")))
                (json context :ok (.entity @orderdb id [])))))
   :error error-handler'})

(def delete-order
  {:name ::order-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  tx (.delete @orderdb id)]
              (if (nil? tx)
                (json context :not-found "No such order")
                (json context :ok "Success"))))
   :error error-handler'})