(ns helping-hands.consumer.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.consumer.persistence :as p]))

(defstate consumerdb
  :start (p/create-consumer-database)
  :stop (.close consumerdb))