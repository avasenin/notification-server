(ns echo.notificationserver.router-test
  (:use [clojure.test])
  (:require
    [clj-time.core]
    [clj-time.coerce]
    [clojure.tools.logging :as logging]
    [echo.notificationserver.router :as router]))

(deftest test-satisfies-clause?
  ; pass cases
  (are [clause n] (@#'router/satisfies-clause? clause n)
       {:key "project" :values ["ds"]} {:notifications/signature ["project:ds"]}
       {:key "severity" :values ["critical"]} {:notifications/tags ["project:ds" "severity:critical"]}
       {:key "severity" :values ["warning" "critical"]} {:notifications/tags ["severity:critical"]})
       {:key "severity" :values ["warning" "critical"]} {:notifications/tags ["severity:probablyfine"]}
       {:key "severity" :values ["warning" "critical"] :negative? true} {:notifications/tags ["severity:critical"]} 
  ; block cases
  (are [clause n] (not (@#'router/satisfies-clause? clause n))
       {:key "foo" :values ["bar"]} {:notifications/tags ["foo:baz" "foo:bar0" "bar:foo" ""]}
       {:key "severity" :values ["warning"]} {:notifications/tags ["severity:error"]}
       {:key "severity" :values ["warning"] :negative? true} {:notifications/tags ["severity:warning"]}))

(def ^:private always-on
  {:schedule/title      "24/7"
   :schedule/tz         "Asia/Novosibirsk"
   :schedule/period     "1w"
   :schedule/onoff      (-> (for [day (range 7) hour (range 24)] [day hour]) set str)
   :schedule/connectors [:connector.type/phone :connector.type/email]})

(def ^:private always-off
  {:schedule/title      "off"
   :schedule/tz         "Asia/Novosibirsk"
   :schedule/period     "1w"
   :schedule/onoff      "#{}"
   :schedule/connectors [:connector.type/phone :connector.type/email]})

(def ^:private nsk-day
  {:schedule/title      "9-to-5"
   :schedule/tz         "Asia/Novosibirsk"
   :schedule/period     "1w"
   :schedule/onoff      (-> (for [day (range 7) hour (range 9 17)] [day hour]) set str)
   :schedule/connectors [:connector.type/email]})

(def ^:private ulsk-night
  {:schedule/title      "0-to-09"
   :schedule/tz         "Europe/Moscow"
   :schedule/period     "1w"
   :schedule/onoff      (-> (for [day (range 7) hour (range 0 9)] [day hour]) set str)
   :schedule/connectors [:connector.type/phone]})


(def ^:private odd-even-week
  {:schedule/title      "odd-even week"
   :schedule/tz         "Asia/Novosibirsk"
   :schedule/period     "2w"
   :schedule/onoff      (-> (for [hour (range 15 17)] [7 hour]) set str) ; Monday even week
   :schedule/connectors [:connector.type/phone]})

(deftest test-satisfies-schedule?
  ; pass cases
  (are [schedule timestamp]
       (@#'router/satisfies-schedule? schedule timestamp)
       always-on      (clj-time.core/now)
       always-on      (clj-time.core/date-time 2013 10 31  7  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31  2  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31  5  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31  9 59 59)
       ulsk-night     (clj-time.core/date-time 2013 10 31 21  0  0)
       ulsk-night     (clj-time.core/date-time 2013 11  1  0 59 59)
       ulsk-night     (clj-time.core/date-time 2013 10 31 23 59 59)
       odd-even-week  (clj-time.core/date-time 2013 12 16  8 00 00)
       odd-even-week  (clj-time.core/date-time 2013 12 16  9 59 59))
  ; block cases

  (are [schedule timestamp]
       (not (@#'router/satisfies-schedule? schedule timestamp))
       always-off     (clj-time.core/now)
       always-off     (clj-time.core/date-time 2013 10 31  7  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31 10  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31  0  0  0)
       nsk-day        (clj-time.core/date-time 2013 10 31  1 59 59)
       ulsk-night     (clj-time.core/date-time 2013 10 31 18 59 59)
       ulsk-night     (clj-time.core/date-time 2013 10 31 11 11 11)
       ulsk-night     (clj-time.core/date-time 2013 10 31  5  0  0)
       odd-even-week  (clj-time.core/date-time 2013 12 9  8 00 00)
       odd-even-week  (clj-time.core/date-time 2013 12 9  9 59 59)
       odd-even-week  (clj-time.core/date-time 2013 12 16  7 59 59)
       odd-even-week  (clj-time.core/date-time 2013 12 16  10 00 00)
       odd-even-week  (clj-time.core/date-time 2013 12 23  8 00 00)
       odd-even-week  (clj-time.core/date-time 2013 12 23  9 59 59)))

(def ^:private user1
  {:user/name "Bender"
   :user/filters [{:filter/value "severity:critical"} {:filter/value "tag:blackjack"} {:filter/value "tag:hookers"}]
   :user/schedules  [always-on]})

(def ^:private user2
  {:user/name "Leela"
   :user/filters  [{:filter/value "tag:mutant severity:warning,critical"}]
   :user/schedules  [always-on]})

(def ^:private user3
  {:user/name "You don't notify him, he notifies you."
   :user/filters  [{:filter/value "severity:info"}]
   :user/schedules  [always-off]})

(def ^:private two-schedules
  {:user/name "Andy Warhol"
   :user/filters  [{:filter/value "tag:mutant severity:warning,critical"}]
   :user/schedules  [nsk-day odd-even-week]})

(defn- mk-notification
  "Creates a notification with timestamp initialized with current time"
  [timestamp & kvs]
  (apply hash-map :notifications/timestamp timestamp kvs))

(deftest test-choose-recipient
  (are [user tags] (@#'router/choose-recipient user (mk-notification (java.util.Date.) :notifications/tags tags))
       user1 ["severity:critical" "tag:foo" "tag:bar"]
       user1 ["severity:info" "tag:foo" "tag:bar" "tag:blackjack"]
       user1 ["severity:info" "tag:foo" "tag:bar" "tag:hookers"]
       user1 ["severity:info" "tag:blackjack" "tag:hookers"]
       user2 ["severity:warning" "tag:foo" "tag:bar" "tag:mutant"])

  (are [recepient date] (= recepient
                           (@#'router/choose-recipient two-schedules 
                                (mk-notification (.toDate date) :notifications/tags ["severity:warning" "tag:foo" "tag:bar" "tag:mutant"])))

    [two-schedules '(:connector.type/email :connector.type/phone)] (clj-time.core/date-time 2013 12 16 8 0 0)
    nil (clj-time.core/date-time 2013 12 23 1 0 0)
    [two-schedules '(:connector.type/email)] (clj-time.core/date-time 2013 12 23 8 0 0)
    nil (clj-time.core/date-time 2013 12 16 19 0 0))

  (are [user tags] (not (@#'router/choose-recipient user (mk-notification (java.util.Date.) :notifications/tags tags)))
       user1 ["severity:warning" "tag:foo" "tag:bar"]
       user2 ["severity:info" "tag:foo" "tag:bar" "tag:blackjack"]
       user2 ["severity:info" "tag:foo" "tag:bar" "tag:hookers"]
       user2 ["severity:info" "tag:blackjack" "tag:hookers"]
       user1 ["severity:info" "tag:foo" "tag:bar" "tag:mutant"]
       user2 ["severity:info" "tag:mutant"]
       user3 ["severity:info" "tag:soviet"]))

(deftest test-choose-recipients
  (are [recepients tags]
       (= recepients (router/choose-recipients [user1 user2 user3] (mk-notification (java.util.Date.) :notifications/tags tags)))
       [[user1 '(:connector.type/email :connector.type/phone)] [user2 '(:connector.type/email :connector.type/phone)]] ["severity:critical" "tag:mutant"]
       [[user1 '(:connector.type/email :connector.type/phone)]] ["severity:info" "tag:blackjack"]
       [[user2 '(:connector.type/email :connector.type/phone)]] ["severity:warning" "tag:mutant"]
       []                         ["severity:warning" "tag:nobody" "tag:cares"]))

