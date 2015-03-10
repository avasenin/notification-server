(ns echo.notificationserver.filter-parser
  (:require
    [clojure.string :as string]
    [echo.notificationserver.core :as core])
  (:import
    [java.util Date]))

(defn- parse-clause [clause]
  (let [[key0 values0] (string/split clause #":")]
    (when (and key0 values0
               (not (string/blank? key0))
               (not (string/blank? values0)))
      (let [negative?      (= (first key0) \-)
            key            (if negative? (.substring key0 1) key0)
            values         (string/split values0 #",")]
        {:key key :values values :negative? negative?}))))

(defn parse-filter [line]
  (doall (keep parse-clause (-> line string/trim (string/split #"\s+")))))
