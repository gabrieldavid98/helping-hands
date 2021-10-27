(ns helping-hands.alert.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.alert.channel :as c]))

(defstate alert-consumer
  :start (c/create-kafka-consumer)
  :stop (.close alert-consumer))