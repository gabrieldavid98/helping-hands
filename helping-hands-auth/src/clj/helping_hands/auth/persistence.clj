(ns helping-hands.auth.persistence
  (:require [agynamix.roles :as r]
            [cheshire.core :as jp]
            [bcrypt-clj.auth :as a]))

(defn get-bcrypt
  "Creates a Bcrypt hash of the password"
  [creds]
  (a/crypt-password creds))

(def userdb
  (atom {:secret nil
         :roles {"hh/superadmin" "*"
                 "hh/admin" "hh:*"
                 "hh/notify" #{"hh:notify" "notify/alert"}
                 "notify/alert" #{"notify:email" "notify:sms"}}
         :users {"hhuser" {:pwd (get-bcrypt "hhuser")
                           :roles #{"hh/notify"}}
                "hhadmin" {:pwd (get-bcrypt "hhadmin")
                           :roles #{"hh/admin"}}
                "superadmin" {:pwd (get-bcrypt "superadmin")
                              :roles #{"hh/superadmin"}}}}))

(defn has-access?
  "Checks for relevant permission"
  [uid perms]
  (r/has-permission? (-> @userdb :users (get uid)) perms))

(defn init-db
  "Initialize the roles for permission framework"
  []
  (r/init-roles (:roles @userdb))
  userdb)

(comment (defprotocol ConsumerDb
   "Abstraction for consumer database"
   (upsert [this id name address mobile email geo]
     "Adds/Updates a consumer entity")
   (entity [this id flds]
     "Gets the specified consumer with all or requested fields")
   (delete [this id]
     "Deletes the specified consumer entity")
   (close [this]
     "Closes the database"))

 (defn- get-entity
   [node id fields]
   (let [fields (if (empty? fields) '[*] fields)]
     (xt/pull (xt/db node) fields id)))

 (defrecord ConsumerDbXtDb [node]
   ConsumerDb
   (upsert [_ id name address mobile email geo]
     (let [current-consumer (get-entity node id [])]
       (->> (xt/submit-tx node [[:put
                                 (->> {:xt/id id
                                       :consumer/name name
                                       :consumer/address address
                                       :consumer/mobile mobile
                                       :consumer/email email
                                       :consumer/geo geo}
                                      (filter (comp some? val))
                                      (into {})
                                      (merge current-consumer))]])
            (xt/await-tx node))))
   (entity [_ id fields]
     (get-entity node id fields))
   (delete [_ id]
     (->> (xt/submit-tx node [[:delete id]])
          (xt/await-tx node)))
   (close [_]
     (.close node)))

 (defn create-consumer-database
   "Creates a consumer database and returns the connection"
   []
   (let [home-dir (cfg/get-conf :home)
         rocksdb-dir (str home-dir (if (u/windows?) "\\.rocksdb\\" "/.rocksdb/"))
         ix-store (u/abs-path->uri (str rocksdb-dir "consumer-ix-store"))
         doc-store (u/abs-path->uri (str rocksdb-dir "consumer-doc-store"))
         tx-log (u/abs-path->uri (str rocksdb-dir "consumer-tx-log"))]
     (->> (xt/start-node
           {:xtdb/index-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                          :db-dir ix-store}}
            :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                             :db-dir doc-store}}
            :xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                     :db-dir tx-log}}})
          (->ConsumerDbXtDb)))))