(ns helping-hands.provider.persistence
  "Persistence Port and Adapter for Provider Service"
  (:require [xtdb.api :as xt]
            [helping-hands.provider.utils :as u]
            [helping-hands.provider.config :as cfg]))

(defprotocol ProviderDb
  "Abstraction for privider database"
  (upsert [this id name mobile since rating]
          "Updates a provider entity")
  (entity [this id fields]
          "Gets the specified provider with all or requested fields")
  (delete [this id]
          "Deletes the specified provider entity")
  (close [this]
         "Closes the database"))

(defn- get-entity [node id fields]
  (let [fileds-to-pull (if (empty? fields) '[*] fields)]
    (xt/pull (xt/db node) fileds-to-pull id)))

(defrecord ProviderDbXtDb [node]
  ProviderDb
  (upsert [_ id name mobile since rating]
    (let [current-provider (get-entity node id [])]
      (->> (xt/submit-tx node [[::xt/put
                                (->> {:xt.db/id id
                                      :provider/name name
                                      :provider/mobile mobile
                                      :provider/since since
                                      :provider/rating rating}
                                     (filter (comp some? val))
                                     (into {})
                                     (merge current-provider))]])
           (xt/await-tx node))))
  (entity [_ id fields]
    (get-entity node id fields))
  (delete [_ id]
    (->> (xt/submit-tx node [[::xt/delete id]])
         (xt/await-tx node)))
  (close [_]
        (.close node)))

(defn create-provider-database 
  "Creates a provider database and returns the connection"
  []
  (let [home-dir (cfg/get-conf :home)
        rocksdb-dir (str home-dir (if (u/windows?) "\\.rocksdb\\" "/.rocksdb/"))
        ix-store (u/abs-path->uri (str rocksdb-dir "provider-ix-store"))
        doc-store (u/abs-path->uri (str rocksdb-dir "provider-doc-store"))
        tx-log (u/abs-path->uri (str rocksdb-dir "provider-tx-log"))]
    (->> (xt/start-node
          {:xtdb/index-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                         :db-dir ix-store}}
           :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                            :db-dir doc-store}}
           :xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                    :db-dir tx-log}}})
         (->ProviderDbXtDb))))