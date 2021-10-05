(ns helping-hands.provider.state
  (:require [mount.core :refer [defstate]]
            [helping-hands.provider.persistence :as p]))

(defstate providerdb
  :start (p/create-provider-database)
  :stop (.close providerdb))