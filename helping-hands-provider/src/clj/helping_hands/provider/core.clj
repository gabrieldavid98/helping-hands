(ns helping-hands.provider.core
  "Initialize Helping Hands Provider Service"
  (:require [clojure.string :as s]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.provider.http :refer [json]]
            [helping-hands.provider.state :refer [providerdb]])
  (:import [java.io IOException]
           [java.util UUID]))

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
       (json context :bad-request (str "ID, mobile is mandatory "
                                       "and rating, since must be a number"))))))

(defn error-handler'
  "Handles interceptor errors"
  [context ex-info]
  (json context :internal-server-error (.getMessage ex-info)))

(def validate-id
  {:name ::validate-id
   :enter (fn [context]
            (if-let [_ (or (-> context :request :form-params :id)
                           (-> context :request :query-params :id)
                           (-> context :request :path-params :id))]
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid Provider ID"))))
   :error error-handler'})

(def validate
  {:name ::validate
   :enter (fn [context]
            (if-let [_ (-> context :request :form-params)]
              (prepare-valid-context context)
              (chain/terminate
               (json context :bad-request "Invalid parameters"))))
   :error error-handler'})

;; ----------------------------------------
;; Business Logic Interceptors
;; ----------------------------------------

(def get-provider
  {:name ::provider-get
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (-> tx-data :id UUID/fromString)
                  entity (.entity providerdb id (:fields tx-data))]
              (if (empty? entity)
                (json context :not-found "Not such provider")
                (json context :ok entity))))
   :error error-handler'})

(def upsert-provider
  {:name ::provider-upsert
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (if-let [id* (:id tx-data)]
                       (UUID/fromString id*)
                       (UUID/randomUUID))
                  tx (.upsert providerdb id (:name tx-data)
                              (:mobile tx-data) (:since tx-data)
                              (:rating tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Upsert failed for provider: " id)))
                (json context :ok (.entity providerdb id [])))))
   :error error-handler'})

(def create-provider
  {:name ::provider-create
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (UUID/randomUUID)
                  tx (.upsert providerdb id (:name tx-data)
                              (:mobile tx-data) (:since tx-data)
                              (:rating tx-data))]
              (if (nil? tx)
                (throw (IOException.
                        (str "Provider creation failed")))
                (json context :ok (.entity providerdb id [])))))
   :error error-handler'})

(def delete-provider
  {:name ::provider-delete
   :enter (fn [context]
            (let [tx-data (:tx-data context)
                  id (if-let [id* (:id tx-data)]
                       (UUID/fromString id*)
                       (UUID/randomUUID))
                  tx (.delete providerdb id)]
              (if (nil? tx)
                (json context :not-found "Not such provider")
                (json context :ok "Success"))))
   :error error-handler'})

