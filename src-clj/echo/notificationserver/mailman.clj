(ns echo.notificationserver.mailman
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [clj-http.client :as client]
    [postal.core :as postal]
    [echo.notificationserver.router :as router]
    [echo.notificationserver.db :as db]
    [echo.notificationserver.core :as core]))

(declare perform-task)

(defn store [conn attrs]
  (try
    (let [notification (db/create-notification conn attrs)
          recipients  (router/choose-recipients (db/profiles (db/get-db conn)) notification)]
      (if-not (empty? recipients)
        (doseq [[user connectors] recipients
                connector connectors]
          (do
            (logging/info "Create distribution task for" (:user/google-id user) connector)
            (db/create-task conn {:task/user (:db/id user)
                                  :task/connector connector
                                  :task/notification (:db/id notification)})))
        (logging/warn "No recipients for notification" notification)))
    (catch Exception ex
      (logging/error ex "Failed to process notification"))))

(defn worker [conn]
  (loop []
    (try
      (let [task (db/next-task conn)]
        (if task
          (do
            (perform-task task)
            (db/update conn {:db/id (:db/id task) :task/status :done}))
          (Thread/sleep (rand-int 5000))))
      (catch Exception e
        (logging/error e "Something wrong with handling delayed task")))
    (recur)))

(defn run-workers [conn]
  (doseq [i (range 4)]
    (logging/info "Starting mailman worker" i)
    (future (worker conn))))

(defmulti perform-task :task/connector)

(defmethod perform-task :connector.type/phone [{notification :task/notification, user :task/user}]
  (when-not (string/blank? (:user/phone user))
    (let [{:keys [login password]} (get-in (core/config) [:notifier :sms])
          phone (:user/phone user)
          message (:notifications/short-message notification)]
      (logging/info "Sending sms about " message " to " phone (:user/google-id user))
      (client/request {
        :method :get
        :url "http://smsc.ru/sys/send.php"
        :query-params {
          :login login
          :psw password
          :mes message
          :phones phone
          :charset "utf-8"}}))))

(defmethod perform-task :connector.type/email [{notification :task/notification, user :task/user}]
  (when-not (string/blank? (:user/google-id user))
    (let [params (get-in (core/config) [:notifier :email])
          to (:user/google-id user)
          subject (:notifications/short-message notification)
          message (:notifications/message notification)]
      (logging/info "Sending email about " subject " to " to)
      (postal/send-message params {:from (:from params)
                                   :to to
                                   :subject subject
                                   :body message}))))

(defmethod perform-task :connector.type/pushover [{notification :task/notification, user :task/user}]
  (when-not (string/blank? (:user/pushover user))
    (let [recipient (:user/pushover user)
          message (:notifications/short-message notification)]
      (logging/info "Sending pushover about " message " to " recipient (:user/google-id user))
      (client/request {:method :post
                       :url "https://api.pushover.net/1/messages.json"
                       :query-params {:token (get-in (core/config) [:notifier :pushover :token])
                                      :user recipient
                                      :message message}}))))

(defmethod perform-task :connector.type/http [{notification :task/notification, user :task/user}]
  (when-not (string/blank? (:user/http user))
    (let [endpoint (:user/http user)
          subject (:notifications/short-message notification)
          message (:notifications/message notification)]
      (logging/info "Calling http about " message " to " endpoint (:user/google-id user))
      (client/request {
        :method :get
        :url endpoint
        :query-params {
          :subject subject
          :message message}}))))

(defmethod perform-task :default [task]
  (logging/error "Mailman doesn't known how to perform task " task))
