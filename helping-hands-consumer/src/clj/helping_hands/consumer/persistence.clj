(ns helping-hands.consumer.persistence
  (:require [datomic.client.api :as d]))

(defprotocol consumer-db
  "Abstraction for consumer database"
  (upsert [this id name address mobile email geo]
          "Adds/Updates a consumer entity")
  (entity [this id flds]
          "Gets the specified consumer with all or requested fields")
  (delete [this id]
          "Deletes the specified consumer entity"))

(defn- get-entity-id
  [conn id]
  (-> (d/q '[:find ?e
             :in $ ?id
             :where [?e :consumer/id ?id]]
           (d/db conn)
           (str id))
      ffirst))

(defn- get-entity
  [conn id]
  (let [eid (get-entity-id conn id)]
    (d/pull (d/db conn) '[*] eid)))

(defrecord consumer-db-datomic [conn]
  consumer-db
  (upsert [_ id name address mobile email geo]
          (d/transact conn {:tx-data (->> {:db/id id
                                           :consumer/id id
                                           :consumer/name name
                                           :consumer/address address
                                           :consumer/mobile mobile
                                           :consumer/email email
                                           :consumer/geo geo}
                                          (filter (comp some? val))
                                          (into {})
                                          (vector))}))
  (entity [_ id flds]
          (when-let [consumer (get-entity conn id)]
            (if (empty? flds)
              consumer
              (select-keys consumer (map keyword flds)))))
  (delete [_ id]
          (when-let [eid (get-entity-id conn id)]
            (d/transact conn {:tx-data [[:db/retractEntity eid]]}))))

(defn create-consumer-database
  "Creates a consumer database and returns the connection"
  [d]
  (let [cfg {:server-type :dev-local :system "helping-hands-consumer"}
        client (d/client cfg)
        db (d/create-database client {:db-name d})
        conn (d/connect client {:db-name d})]
    (when db
      (d/transact conn {:tx-data [{:db/ident :consumer/id
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Unique Consumer ID"
                                   :db/unique :db.unique/identity}
                                  {:db/ident :consumer/name
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Display Name for the Consumer"}
                                  {:db/ident :consumer/address
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Consumer Address"}
                                  {:db/ident :consumer/mobile
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Consumer Mobile Number"}
                                  {:db/ident :consumer/email
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Consumer Email Address"}
                                  {:db/ident :consumer/geo
                                   :db/valueType :db.type/string
                                   :db/cardinality :db.cardinality/one
                                   :db/doc "Latitude,Longitude CSV"}]}))
    (->consumer-db-datomic conn)))