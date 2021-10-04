(ns helping-hands.order.persistence
  "Persistence Port And Adapter for Order"
  (:require [xtdb.api :as xt]
            [helping-hands.order.utils :as u]
            [helping-hands.order.config :as cfg]))

(defprotocol OrderDb
  "Abstraction for order database"
  (upsert [this id service provider consumer
           cost start end rating status]
    "Adds/Updates an order entity")
  (entity [this id fields]
    "Gets the specified order with all or requested fields")
  (orders [this uid fields]
    "Gets all the orders of the authenticated user with all or requested fields")
  (delete [this id]
    "Deletes the specified order entity")
  (close [this]
    "Closes the database"))

(defn- get-entity [node id fields]
  (let [fields-to-pull (if (empty? fields) '[*] fields)]
    (xt/pull (xt/db node) fields-to-pull id)))

(defn- get-all-entities [node uid]
  (->> (xt/q (xt/db node)
             '{:find [(pull ?e [*])]
               :in [?uid]
               :where [[?e :order/consumer ?uid]]}
             uid)
       (into [])
       (flatten)))

(defrecord OrderDbXtdb [node]
  OrderDb
  (upsert [_ id service provider consumer
           cost start end rating status]
    (let [current-order (get-entity node id [])]
      (->> (xt/submit-tx node [[::xt/put
                                (->> {:xt/id id
                                      :order/service service
                                      :order/provider provider
                                      :order/consumer consumer
                                      :order/cost cost
                                      :order/start start
                                      :order/end end
                                      :order/rating rating
                                      :order/status status}
                                     (filter (comp some? val))
                                     (into {})
                                     (merge current-order))]])
           (xt/await-tx node))))
  (entity [_ id fields]
    (get-entity node id fields))
  (orders [_ uid fields]
    (when-let [orders (get-all-entities node uid)]
      (if (empty? fields)
        orders
        (map #(select-keys % (map keyword fields)) orders))))
  (delete [_ id]
    (->> (xt/submit-tx node [[::xt/delete id]])
         (xt/await-tx node)))
  (close [_]
    (.close node)))

(defn create-order-database
  "Creates a order database and returns the connection"
  []
  (let [home-dir (cfg/get-conf :home)
        rocksdb-dir (str home-dir (if (u/windows?) "\\.rocksdb\\" "/.rocksdb/"))
        ix-store (u/abs-path->uri (str rocksdb-dir "order-ix-store"))
        doc-store (u/abs-path->uri (str rocksdb-dir "order-doc-store"))
        tx-log (u/abs-path->uri (str rocksdb-dir "order-tx-log"))]
    (->> (xt/start-node
          {:xtdb/index-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                         :db-dir ix-store}}
           :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                            :db-dir doc-store}}
           :xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                    :db-dir tx-log}}})
         (->OrderDbXtdb))))

