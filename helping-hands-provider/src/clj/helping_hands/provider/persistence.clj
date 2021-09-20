(ns helping-hands.provider.persistence
  "Persistence Port and Adapter for Provider Service"
  (:require [crux.api :as crux]))

(defprotocol provider-db
  "Abstraction for privider database"
  (upsert [this id name mobile since rating]
          "Updates a provider entity")
  (entity [this id fields]
          "Gets the specified provider with all or requested fields")
  (delete [this id]
          "Deletes the specified provider entity"))

(defn- get-entity [node id fields]
  (let [fileds-to-pull (if (empty? fields) '[*] fields)]
    (crux/pull (crux/db node) fileds-to-pull id)))

(defrecord provider-db-crux [node]
  provider-db
  (upsert [_ id name mobile since rating]
    (->> (crux/submit-tx node [[:crux.tx/put
                                (->> {:crux.db/id id
                                      :provider/name name
                                      :provider/mobile mobile
                                      :provider/since since
                                      :provider/rating rating}
                                     (filter (comp some? val))
                                     (into {}))]])
         (crux/await-tx node)))
  (entity [_ id fields]
          (get-entity node id fields))
  (delete [_ id]
          (->> (crux/submit-tx node [[:crux.tx/delete id]])
               (crux/await-tx node))))

(defn create-provider-database []
  (->> (crux/start-node {})
       (->provider-db-crux)))
