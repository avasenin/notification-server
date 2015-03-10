(ns echo.notificationserver.views.schedule
  (:require
    [clj-time.format]
    [clj-time.core]
    [clojure.tools.logging :as logging]
    [echo.notificationserver.views.layouts :as layouts]
    [echo.notificationserver.util :as util])
  (:import [java.util TimeZone]
           [org.joda.time DateTimeZone]))

(defn timezones []
  (for [tz (->> ["America/Los_Angeles" "America/New_York" "Etc/UTC" "Europe/Moscow" "Asia/Novosibirsk"]
                (map clj-time.core/time-zone-for-id))
        :let [formatter (clj-time.format/formatter "ZZ" tz)]]
    {:id (.getID tz) :name (str "(" (.print formatter (System/currentTimeMillis)) ") " (.getID tz))}))

(defn schedule-template [request model]
  (layouts/default request "Schedule"
    "window.onload = echo.notificationserver.schedule.render();"
    [:form {:action (:uri request) :method "POST" :class "form-horizontal" :role "form"}
      [:div.form-group
        [:label.col-sm-2.control-label {:for "title"} "Title"]
        [:div.col-sm-5
          [:input {:id "title" :class "form-control" :name "schedule/title" :value (:schedule/title model)}]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "tz"} "Time Zone"]
        [:div.col-sm-5
          [:select {:class "form-control" :name "schedule/tz"}
            (for [tz (timezones)]
              [:option (-> {:value (:id tz)}
                           (cond-> (= (or (:schedule/tz model) "Europe/Moscow") (:id tz))
                                   (assoc :selected "selected")))
                       (:name tz)])]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "period"} "Period"]
        [:div.col-sm-3
          [:select {:class "form-control period" :name "schedule/period"}
            (for [[v t] [["1w" "One-week period"] ["2w" "Two-weeks period"]]
                  :let [attrs (-> {:value v} (cond-> (= (:schedule/period model) v)
                                                     (assoc :selected "selected")))]]
              [:option attrs t])]]]

      [:div.form-group
        [:label.col-sm-2.control-label "Notify via"]
        [:div.col-sm-3
            (for [[v t] [["phone" "Phone"] ["email" "Email"] ["pushover" "Pushover"] ["http" "HTTP URL"]]
                  :let [connector-type (str "connector.type/" v)
                        user-field (str "user/" v)
                        attrs (-> {:name "schedule/connectors[]" :id v :value connector-type :type "checkbox"}
                                  (cond->
                                    (or (and (not (:schedule/connectors model)) (not (clojure.string/blank? ((keyword v) (:current-user request)))))
                                        (and (:schedule/connectors model) ((:schedule/connectors model) (keyword connector-type))))
                                    (assoc :checked "checked")))]]
              [:div [:label {:for v} [:input attrs] " " (if (clojure.string/blank? ((keyword user-field) (:current-user request))) (str t " (not specified)") t) ]])]]

      [:div.form-group
        [:div.col-sm-offset-2.col-sm-4
          [:input {:class "btn btn-primary" :type "submit"}]]]

      [:div.form-group
        [:div.col-sm-offset-2.col-sm-8
          [:div {:class "schedule-container" :config (or (:schedule/onoff model) #{})}]]]]))

