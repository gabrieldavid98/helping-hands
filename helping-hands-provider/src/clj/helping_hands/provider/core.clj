(ns helping-hands.provider.core
  "Initialize Helping Hands Provider Service"
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            [helping-hands.provider.persistence :as p]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.log :as log])
  (:import [java.io IOException]
           [java.util UUID]))

(def ^:private providerdb
  (delay (p/create-provider-database)))

;; ----------------------------------------
;; Validation Interceptors
;; ----------------------------------------

(defn- validate-rating-ts
  "Validate the rating and timestamp"
  [context]
  (let [rating (-> context :request :form-params :rating)
        since-ts (-> context :request :form-params :since)]
    (try
      (let [context (if (not (nil? rating))
                      (assoc-in context [:request :form-params :rating]
                                (Float/parseFloat rating))
                      context)
            context (if (not (nil? since-ts))
                      (assoc-in context [:request :form-params :since]
                                (Long/parseLong since-ts))
                      context)]
        context)
      (catch Exception _ nil))))

(defn- prepare-valid-context
  "Applies validation logic and returns the resulting context"
  [context]
  (let [params (merge (-> context :request :form-params)
                      (-> context :request :query-params)
                      (-> context :request :path-params))
        ctx (validate-rating-ts context)
        params (if (not (nil? ctx))
                 (assoc params
                        :rating (-> ctx :request :form-params :rating)
                        :since (-> ctx :request :form-params :since))
                 params)]
    (if (and (seq params)
             (not (nil? ctx))
             (or (:id params) (:mobile params)))
      (let [fields (if-let [fl (:flds params)]
                     (map s/trim (s/split fl #","))
                     (vector))
            params (assoc params :fields fields)]
        (assoc context :tx-data params))
      (chain/terminate
       (assoc context
              :response {:status 400
                         :body (str "ID, mobile is mandatory "
                                    "and rating, since must be a number")})))))

(defn- error-handler* [context ex-info]
  (assoc context
         :response {:status 500
                    :body (.getMessage ex-info)}))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if-let [_ (or (-> context :request :form-params :id)
                           (-> context :request :query-params :id)
                           (-> context :request :path-params :id))]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid Provider ID"}))))
   :error error-handler*})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if-let [_ (-> context :request :form-params)]
              (prepare-valid-context context)
              (chain/terminate
               (assoc context
                      :response {:status 400
                                 :body "Invalid parameters"}))))
   :error error-handler*})

;; ----------------------------------------
;; Business Logic Interceptors
;; ----------------------------------------

(def get-provider
  {:name ::provider-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (-> tx-data :id UUID/fromString)
                  entity (.entity @providerdb id (:fields tx-data))]
              (if (empty? entity)
                (assoc context :response {:status 404
                                          :body "Not such provider"})
                (assoc context :response {:status 200
                                          :body (jp/generate-string entity)}))))
   :error error-handler*})

(def upsert-provider
  {:name ::provider-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (if-let [id* (:id tx-data)]
                       (UUID/fromString id*)
                       (UUID/randomUUID))
                  tx (.upsert @providerdb id (:name tx-data)
                              (:mobile tx-data) (:since tx-data)
                              (:rating tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Upsert failed for provider: " id)))
                (assoc context
                       :response {:status 200
                                  :body (jp/generate-string
                                         (.entity @providerdb id []))}))))
   :error error-handler*})

(def create-provider
  {:name ::provider-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/randomUUID)
                  tx (.upsert @providerdb id (:name tx-data)
                              (:mobile tx-data) (:since tx-data)
                              (:rating tx-data))]
              (log/info :create-provider [tx-data id tx])
              (if (nil? tx)
                (throw (IOException.
                        (str "Provider creation failed")))
                (assoc context
                       :response {:status 200
                                 :body (jp/generate-string
                                        (.entity @providerdb id []))}))))
   :error error-handler*})

(def delete-provider
  {:name ::provider-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (if-let [id* (:id tx-data)]
                       (UUID/fromString id*)
                       (UUID/randomUUID))
                  tx (.delete @providerdb id)]
              (if (nil? tx)
                (assoc context :response {:status 404
                                          :body "No such provider"})
                (assoc context :response {:status 200
                                          :body "Success"}))))
   :error error-handler*})

