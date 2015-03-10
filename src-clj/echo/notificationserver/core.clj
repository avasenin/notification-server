(ns echo.notificationserver.core
  (:require
    [clojure.java.io :as io]
    [nomad :refer [defconfig]])
  (:import
    [java.util Date]))

(defconfig config (io/resource (System/getProperty "config" "config.edn")))

(defn notification-valid? [n]
  (and
    (clojure.set/subset? (set (keys n)) #{:notifications/signature :notifications/tags :notifications/short-message :notifications/message})
    (coll? (:notifications/tags n))
    (coll? (:notifications/signature n))))
