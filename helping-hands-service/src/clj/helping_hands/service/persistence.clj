(ns helping-hands.service.persistence
  (:require [xtdb.api :as xt]))

(defprotocol ServiceDb
  "Abstraction for service database"
  (upsert [this id type provider area cost rating status]
          "Adds/Updates a service entity")
  (entity [this id fields]
          "Gets the specified service with all or requested fields")
  (delete [this id]
          "Deletes the specified entity"))

(defn- get-entity [node id fields]
  (let [fields-to-pull (if (empty? fields) '[*] fields)]
    (xt/pull (xt/db node) fields-to-pull id)))

(defrecord ServiceDbXt [node]
  ServiceDb
  (upsert [_ id type provider area cost rating status]
          (let [current-service (get-entity node id [])]
            (->> (xt/submit-tx node [[::xt/put
                                      (->> {:xt/id id
                                            :service/type type
                                            :service/provider provider
                                            :service/area area
                                            :service/cost cost
                                            :service/rating rating
                                            :service/status status}
                                           (filter (comp some? val))
                                           (into {})
                                           (merge current-service))]])
                 (xt/await-tx node))))
  (entity [_ id fields]
          (get-entity node id fields))
  (delete [_ id]
          (->> (xt/submit-tx node [[::xt/delete id]])
               (xt/await-tx node))))

(defn create-service-database []
  (->> (xt/start-node {})
       (->ServiceDbXt)))