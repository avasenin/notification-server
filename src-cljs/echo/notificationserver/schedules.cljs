(ns echo.notificationserver.schedules
  (:require
    [clojure.string :as string]
    [dommy.core :as dommy]
    [dommy.attrs :as attrs]
    [dommy.utils :as utils]
    moment
    [echo.notificationserver.core :as core]
    [echo.notificationserver.log :as log])
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(deftemplate render-schedule [schedule]
  [:li.refli.schedules__li {:id (:db/id schedule) :data-schedule (pr-str schedule)}
    [:span.delete.glyphicon.glyphicon-remove {:title "Remove this schedule"}]
    [:a {:href (str "/schedule/" (:db/id schedule))} [:span.title.ref.schedules__schedule (:schedule/title schedule)]]])

(deftemplate render-schedule-deleted [schedule]
  [:li.refli.schedules__li {:id (:db/id schedule) :data-schedule (pr-str schedule)}
    [:span.title.schedules__schedule_deleted (:schedule/title schedule)]
   ;; TODO Undo does not work now
    [:em.flash [:span "Deleted. " #_[:a.undelete {:href "#"} "Undo"]]]])

(deftemplate render-schedules-list [schedules]
  [:div
    [:h1 "Schedules"]
    [:ul.reflist.schedules
      (concat
        (map render-schedule schedules)
        [[:li.refli.schedules__li
           [:a {:href "/schedule/create"} [:span.title.ref.schedules__schedule "Add new schedule"]]]])]])

(defn delete-schedule [schedule]
  (core/q :delete (str "/schedule/" (:db/id schedule))
    (fn [_]
      (dommy/replace! (core/by-id (:db/id schedule)) (render-schedule-deleted schedule)))))

(defn undelete-schedule [schedule]
  (core/q :post "/schedule" {} (dissoc schedule :id)
    (fn [{id :id}]
      (dommy/replace! (core/by-id (:db/id schedule)) (render-schedule (assoc schedule :db/id id)))
      (dommy/append! (core/by-id id) [:em.flash "Restored"]))))

(defn find-schedule [el]
  (when-let [el-schedule (dommy/closest el "[data-schedule]")]
    (cljs.reader/read-string (attrs/attr el-schedule "data-schedule"))))

(defn log [msg]
  (.log js/console msg))

(defn render-schedules
  ([] (render-schedules nil))
  ([highlight]
    (core/q :get "/schedules/list"
      (fn [schedules] (render-schedules schedules highlight))))
  ([schedules highlight]
   (log (str "Rendering" schedules))
    (dommy/replace-contents! (sel1 :.container) (render-schedules-list schedules))
    (dommy/append! (core/by-id (:id highlight)) [:em.flash (:message highlight)])
    (dommy/listen! [(sel1 :.schedules) :.delete] :click
      (fn [e] (delete-schedule (find-schedule (.-target e)))))
    ;; TODO does not work at this moment
    #_(dommy/listen! [(sel1 :.schedules) :.undelete] :click
      (fn [e]
        (.preventDefault e)
        (undelete-schedule (find-schedule (.-target e)))))
   ))

