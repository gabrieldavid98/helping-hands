(ns helping-hands.service.persistence
  (:require [xtdb.api :as xt]
            [helping-hands.service.utils :as u]))

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

(defrecord ServiceDbXtDb [node]
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

(defn create-service-database 
  "Creates a service database and returns the connection"
  []
  (let [home-dir (System/getenv "HOME")
        rocksdb-dir (str home-dir (if (u/windows?) "\\.rocksdb\\" "/.rocksdb/"))
        ix-store (u/abs-path->uri (str rocksdb-dir "service-ix-store"))
        doc-store (u/abs-path->uri (str rocksdb-dir "service-doc-store"))
        tx-log (u/abs-path->uri (str rocksdb-dir "service-tx-log"))]
    (->> (xt/start-node
          {:xtdb/index-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                         :db-dir ix-store}}
           :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                            :db-dir doc-store}}
           :xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                    :db-dir tx-log}}})
         (->ServiceDbXtDb))))