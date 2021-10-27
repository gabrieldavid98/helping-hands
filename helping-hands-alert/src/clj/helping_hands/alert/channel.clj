(ns helping-hands.alert.channel
  (:require [cheshire.core :as jp]
            [clojure.string :as s]
            ;[clojure.core.async :refer [chan <! >!! go-loop]]
            [io.pedestal.log :as log]
            [clojure.tools.logging :as clog]
            [helping-hands.alert.config :as cfg])
  (:import [java.util Collections Properties]
           [org.apache.kafka.common.serialization
            LongDeserializer StringDeserializer]
           [org.apache.kafka.clients.consumer
            Consumer ConsumerConfig KafkaConsumer]))

(defn create-kafka-consumer
  "Creates a new Kafka Consumer"
  []
  (let [props (doto (Properties.)
                (.putAll (cfg/get-conf :kafka))
                (.put ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG
                      (.getName StringDeserializer))
                (.put ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG
                      (.getName StringDeserializer)))
        consumer (KafkaConsumer. props)
        _ (.subscribe consumer (Collections/singletonList
                                (get (cfg/get-conf :kafka) "topic")))]
    consumer))

(def ^:private closed? (atom false))

(defn consume-records
  "Consume the records using given consumer"
  [consumer result]
  (while (not @closed?)
    (doseq [record (.poll consumer 1000)]
      (try
        (let [rmsg (jp/parse-string (.value record))
              msg (->> {:from "abc@abc.com"
                        :to (get rmsg "to" "to@abc.com")
                        :cc (rmsg "cc")
                        :subject (rmsg "subject")
                        :body (rmsg "body")}
                       (filter (comp some? val))
                       (into {}))]
          (log/info :email msg))
        (catch Exception e
          (clog/error "Failed to send email" e)))
      (swap! result conj record))
    (Thread/sleep 5000)))

(defn capture-records
  "Consume the records using given consumer"
  [consumer result]
  (while (not @closed?)
    (doseq [record (.poll consumer 1000)]
      (swap! result conj record))
    (Thread/sleep 5000)))

(defn stop-consuming []
  (reset! closed? true))

(defn start-consuming []
  (when @closed?
    (reset! closed? false)))
