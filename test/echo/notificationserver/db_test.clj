(ns echo.notificationserver.db-test
  (:use clojure.test)
  (:import [java.util Date])
  (:require [datomic.api :as d]
            [clojure.tools.logging :as logging]
            [echo.notificationserver.util :as util]
            [echo.notificationserver.db :as db]))

(deftest base-db-operation
  (let [conn (db/connect "datomic:mem://notifications-tests")
        user (db/create conn {:user/name "Test user" :user/google-id "test@gmail.com"})]
    (testing "that user exists"
      (is (= "Test user" (:user/name (db/by-attr (db/get-db conn) :user/google-id "test@gmail.com"))))
      (is (contains? (->> (db/profiles (db/get-db conn))
                          (map util/entity->map)
                          (map :user/google-id)
                          set) "test@gmail.com")))

    (testing "schedules"
      (let [schedule (db/create-schedule conn user {:schedule/title "Nightly shift"
                                                    :schedule/onoff "#{[0 0] [0 1] [0 2] [0 3] [0 4]}"
                                                    :schedule/period "1w"
                                                    :schedule/tz "Asia/Novosibirsk"
                                                    :schedule/connectors [:connector.type/phone]})]
        (is (= "Nightly shift" (:schedule/title schedule)))
        (is (= "#{[0 0] [0 1] [0 2] [0 3] [0 4]}" (:schedule/onoff schedule)))
        (is (= "1w" (:schedule/period schedule)))
        (is (= "Asia/Novosibirsk" (:schedule/tz schedule)))
        (is (= #{:connector.type/phone} (:schedule/connectors schedule)))
        (is (= (-> (db/by-attr (db/get-db conn) :user/google-id "test@gmail.com") :user/schedules first :db/id) (:db/id schedule)))
        (testing "Update schedule"
          (let [updated-schedule (db/update-schedule conn (-> schedule
                                                              util/entity->map
                                                              (assoc :schedule/connectors [:connector.type/email])))]
            (is (= (:db/id updated-schedule) (:db/id schedule)))
            (is (= #{:connector.type/email} (:schedule/connectors updated-schedule)))))))
    (testing "notifications"
      (let [n1 (db/create-notification conn {:notifications/tags ["severity:bro" "tags:foo"],
                                             :notifications/timestamp (java.util.Date. 2014 07 01)})
            n2 (db/create-notification conn {:notifications/signature ["project:test"]
                                             :notifications/timestamp (java.util.Date. 2014 07 02)})
            n3 (db/create-notification conn {:notifications/timestamp (java.util.Date. 2014 07 03)})
            tags (db/tags (db/get-db conn)) ]
        (is (= (contains? tags "tags:foo")))
        (is (= (contains? tags "severity:bro")))
        (is (= #{"project:test"} (:notifications/signature n2)))
        (is (= #{"severity:bro" "tags:foo"} (:notifications/tags n1)))
        (is (= 3 (count (db/notifications-after-e (db/get-db conn)))))
        (is (= [(:db/id n1)] (map :db/id (db/notifications-after-e (db/get-db conn) (:db/id n2)))))))
    (testing "tasks"
      (binding [db/*relax-time* 0
                db/*max-task-attempts* 2]
        (let [new-task (db/create-task conn {})
              tid (:db/id new-task)]
          (is (= [tid tid nil] (map :db/id (take 3 (repeatedly #(db/next-task conn)))))))))))
