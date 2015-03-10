(ns echo.notificationserver.router
  (:require
    [clojure.tools.logging :as logging]
    [clojure.edn :as edn]
    [clj-time.core]
    [clj-time.coerce]
    [echo.notificationserver.filter-parser :as filter-parser]
    [echo.notificationserver.core :as core])
  (:import
    [java.util Date]))

(defn- satisfies-schedule?  [schedule timestamp]
  (let [timezone       (clj-time.core/time-zone-for-id (:schedule/tz schedule))
        local-timestamp (clj-time.core/to-time-zone timestamp timezone)
        hour (clj-time.core/hour local-timestamp)
        day-of-week (dec (clj-time.core/day-of-week local-timestamp))
        onoff (-> (:schedule/onoff schedule) edn/read-string)]
    (case (:schedule/period schedule)
      "1w" (contains? onoff [day-of-week hour])
      "2w" (let [offset (* 7 (mod (.getWeekOfWeekyear local-timestamp) 2))]
             (contains? onoff [(+ offset day-of-week) hour])))))

(defn- satisfies-clause?
  [{k :key vs :values neg :negative?} n]
  (if neg
    (not (satisfies-clause? {:key k :values vs} n))
    (let [all-tags (concat (:notifications/signature n)
                           (:notifications/tags n))
          interesting-tags (set (map #(str k ":" %) vs))]
      (some interesting-tags all-tags))))

(defn satisfies-filter?
  [clauses n]
  (every? #(satisfies-clause? % n) clauses))

(defn- safely-parse-filter
  [filter]
  (try
    (filter-parser/parse-filter filter)
    (catch Exception e
      (logging/error e "Failed to parse filter" filter))))


(defn- choose-recipient
  [user notification]
  (try
    (when (or (empty? (:user/filters user))
              (->> (:user/filters user)
                   (map :filter/value)
                   (map safely-parse-filter)
                   (some #(satisfies-filter? % notification))))
      (when-let [connectors (->> (:user/schedules user)
                              (filter #(satisfies-schedule? % (clj-time.coerce/from-date (:notifications/timestamp notification))))
                              (mapcat :schedule/connectors)
                              set sort not-empty)]
        [user connectors]))
      (catch Exception e
        (logging/error e "Failed to determine recepient connectors" user notification))))

(defn choose-recipients
  [users notification]
  (keep #(choose-recipient % notification) users))
