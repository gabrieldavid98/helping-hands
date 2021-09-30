(ns helping-hands.provider.http
  (:require [cheshire.core :as jp]))

(defn- response [context status body]
  (assoc context
         :response {:status status
                    :body body}))

(defn- api-response [body error]
  {:timestamp (System/currentTimeMillis)
   :error error
   :data body})

(defn- ok
  "Creates a Ok 200 response for a context"
  [context body]
  (response context 200 body))

(defn- created
  "Creates a Created 201 reponse for a context"
  [context body]
  (response context 200 body))

(defn- not-found
  "Creates a Not Found 404 response for a context"
  [context body]
  (response context 404 body))

(defn- bad-request
  "Creates a Bad Request 400 response for a context"
  [context body]
  (response context 400 body))

(defn- internal-server-error
  "Creates a Internal Server Error 500 for a context"
  [context body]
  (response context 500 body))

(defn- unauthorized
  "Creates a Unauthorized 401 for a context"
  [context body]
  (response context 401 body))

(defn json
  "Creates a json response for a context"
  [context http-status-type body]
  (condp = http-status-type
    :ok
    (->> (api-response body nil)
         (jp/generate-string)
         (ok context))
    :created
    (->> (api-response body nil)
         (jp/generate-string)
         (created context))
    :not-found
    (->> (api-response nil body)
         (jp/generate-string)
         (not-found context))
    :bad-request
    (->> (api-response nil body)
         (jp/generate-string)
         (bad-request context))
    :internal-server-error
    (->> (api-response nil body)
         (jp/generate-string)
         (internal-server-error context))
    :unauthorized
    (->> (api-response nil body)
         (jp/generate-string)
         (unauthorized context))))