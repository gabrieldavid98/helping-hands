(ns helping-hands.order.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.order.persistence :as p]))

(defstate orderdb
  :start (p/create-order-database)
  :stop (.close orderdb))