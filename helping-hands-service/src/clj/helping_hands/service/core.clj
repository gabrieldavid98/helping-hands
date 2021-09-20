(ns helping-hands.service.core
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.service.persistence :as p]
            [io.pedestal.interceptor.chain :as chain])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private servicedb (delay (p/create-service-database)))

(defn- validate-rating-cost
  "Validates the rating cost"
  [context]
  (let [rating (-> context :request :form-params :rating)
        cost (-> context :request :form-params :cost)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Double/parseDouble rating))
                      context)
            context (if (not (nil? cost))
                       (assoc-in context [:request :form-params :cost]
                                 (Double/parseDouble cost))
                       context)]
        context)
      (catch Exception _ nil))))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx (validate-rating-cost context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :cost (-> ctx :request :form-params :cost))
                 params)]
    (if (and (seq params)
             (not (nil? ctx))
             (:id params true)
             (:type params)
             (:provider params)
             (:area params)
             (:cost params)
             (contains? #{"A" "NA" "D"} (:type params)))
      (let [fields (if-let [fields' (:fields params)]
                     (map s/trim (s/split fields' #","))
                     (vector))
            params (assoc params :fields fields)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                        :body (str "ID, type, provider, area and cost is mandatory "
                                   "and rating, cost must be a number with type "
                                   "having one if values A, NA or D")})))))

(defn error-handler' [context ex-info]
  (assoc context
         :response {:status 500
                    :body (.getMessage ex-info)}))

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
                                 :body "Invalid Service ID"}))))
   :error error-handler'})

(def validate-id-get
  {:name ::validate-id-get
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
                                     :body "Invalid Service ID"}))))
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid Service ID"}))))
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

(def get-service
  {:name ::service-get
   :enter (fn [context]
            (let [id (-> context :tx-data :id UUID/fromString)
                  fields (-> context :tx-data :fields)
                  entity (.entity @servicedb id fields)]
              (if (empty? entity)
                (assoc context
                       :response {:status 404
                                  :body "No such service"})
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string entity)}))))
   :error error-handler'})

(def upsert-service
  {:name ::service-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (if-let [id' (:id tx-data)]
                       (UUID/fromString id')
                       (UUID/randomUUID))
                  tx (.upsert @servicedb id (:type tx-data) (:provider tx-data)
                              (:area tx-data) (:cost tx-data) (:rating tx-data)
                              (:status tx-data))]
              (if (nil? tx)
                (throw (IOException. 
                        (str "Upsert failed for service: " id)))
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @servicedb id []))}))))})

(def create-service
  {:name ::service-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/randomUUID)
                  tx (.upsert @servicedb id (:type tx-data) (:provider tx-data)
                              (:area tx-data) (:cost tx-data) (:rating tx-data)
                              (:status tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Service creation failed")))
                (assoc context
                       :response {:status 201
                                  :body (jp/generate-string
                                         (.entity @servicedb id []))}))))
   :error error-handler'})

(def delete-service
  {:name ::service-delete
   :enter (fn [context]
            (let [id (-> context :tx-data :id)
                  tx (.delete @servicedb id)]
              (if (nil? tx)
                (throw (IOException.
                        (str "Delete failed for service: " id)))
                (assoc context
                       :response {:status 200
                                  :body "Success"}))))
   :error error-handler'})