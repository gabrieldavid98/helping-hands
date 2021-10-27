(ns helping-hands.alert.config
  "Defines Configuration for the service"
  (:require [omniconf.core :as cfg]))

(defn init-config
  "Initializes the configuration"
  [{:keys [cli-args] :or {cli-args []}}]
  (cfg/define
   {:conf {:type :file
           :required true
           :verifier omniconf.core/verify-file-exists
           :description "MECBOT configuration file"}
    :home {:type :string
           :required true
           :description "Home env variable"}
    :kafka {:type :edn
            :default {"bootstrap.servers" "172.18.198.125:9092"
                      "group.id" "alerts"
                      "topic" "hh-alerts"}
            :description "Kafka Consumer Configuration"}})
  (cfg/populate-from-env)
  (cfg/populate-from-properties)
  (when-let [conf (cfg/get :conf)]
    (cfg/populate-from-file conf))
  (cfg/populate-from-properties)
  (cfg/populate-from-cmd cli-args)
  (cfg/verify))

(defn get-conf
  "Gets the specified config param value"
  [& args]
  (apply cfg/get args))