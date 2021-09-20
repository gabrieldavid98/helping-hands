(ns helping-hands.order.core
  (:require [clojure.string :as s]
            [io.pedestal.interceptor.chain :as chain]
            [cheshire.core :as jp]
            [helping-hands.order.persistence :as p])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private orderdb
  (delay (p/create-order-database)))

(defn error-handler' [context ex-info]
  (assoc context
         :response {:status 500
                    :body (.getMessage ex-info)}))

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
  [service]
  true)

(defn- provider-exists?
  "Validates the provider via Provider Service"
  [provider]
  true)

(defn- consumer-exists?
  "Validates the consumer via Consumer Service"
  [consumer]
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
       (assoc context
              :response {:status 400
                         :body (str "ID, service, provider, consumer, "
                                    "cost and status is mandatory. start/end, "
                                    "rating and cost must be a number "
                                    "with status having one of values O, I, D or C")})))))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if (or (-> context :request :form-params :id)
                    (-> context :request :query-params :id)
                    (-> context :request :path-params :id))
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid Order ID"}))))
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
                   (assoc context
                          :response {:status 400
                                     :body "Invalid Order ID"}))))
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid Order ID"}))))
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
               (assoc context
                      :response {:status 400
                                 :body "Invalid Parameters"}))))
   :error error-handler'})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if (-> context :request :form-params)
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid parameters"}))))
   :error error-handler'})

(def get-order
  {:name ::order-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  entity (.entity @orderdb id (:fields tx-data))]
              (if (empty? entity)
                (assoc context :response {:status 404
                                          :body "No such order"})
                (assoc context :response {:status 200
                                          :body (jp/generate-string entity)}))))
   :error error-handler'})

(def get-all-orders
  {:name ::order-get-all
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  uid (UUID/fromString (:uid tx-data))
                  orders (.orders @orderdb uid (:fields tx-data))]
              (if (empty? orders)
                (assoc context :response {:status 404
                                          :body "No such orders"})
                (assoc context :response {:status 200
                                          :body (jp/generate-string orders)}))))
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
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @orderdb id []))}))))
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
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @orderdb id []))}))))
   :error error-handler'})

(def delete-order
  {:name ::order-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/fromString (:id tx-data))
                  tx (.delete @orderdb)]
              (if (nil? tx)
                (assoc context :response {:status 404
                                          :body "No such order"})
                (assoc context :response {:status 200
                                          :body "Success"}))))
   :error error-handler'})