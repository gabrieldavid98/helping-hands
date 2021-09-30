(ns helping-hands.provider.utils
  (:require [clojure.string :as s]
            [clojure.java.io :as io]))

(defn windows? []
  (-> (System/getProperty "os.name")
      (s/lower-case)
      (s/starts-with? "win")))

(defn abs-path->uri
  "Converts absolute path to uri"
  [abs-path]
  (->> abs-path (io/as-file) (.toURI) (str)))