(ns helping-hands.auth.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.auth.jwt :as jwt]
            [helping-hands.auth.persistence :as p]))

(defstate auth-db
  :start (let [db (p/init-db)]
           (when-not (:secret @db)
             (swap! db #(assoc % :secret (jwt/get-secret)))
             @db))
  :stop nil)