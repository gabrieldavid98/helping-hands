(ns helping-hands.service.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.service.persistence :as p]))

(defstate servicedb
  :start (p/create-service-database)
  :stop (.close servicedb))