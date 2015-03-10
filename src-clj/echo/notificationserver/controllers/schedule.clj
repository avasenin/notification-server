(ns echo.notificationserver.controllers.schedule
  (:require
    [clojure.string :as string]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [ring.util.response :as response]
    [compojure core handler route]
    [cemerick.friend :as friend]
    [echo.notificationserver.util :as util]
    [echo.notificationserver.db :as db]
    [echo.notificationserver.views.schedule :as views.schedule]
    [echo.notificationserver.views.layouts :as layouts]))

(defn- build-model [params]
  (->
    (util/extract-attrs [:schedule] params)
    (update-in [:schedule/title] #(if-not (string/blank? %) % "Unknown schedule"))))

(defn check-access! [user id]
  (when-not (some->> (:user/schedules user)
                     (util/find-first :db/id id))
    (util/raise+ {:message "Can't find id in user scope" :code 404})))

(compojure.core/defroutes routes
  (compojure.core/context "/schedules" []
    (compojure.core/GET "/all" request
      (friend/authenticated
        (layouts/default request "Schedules" "echo.notificationserver.schedules.render_schedules();" nil)))

    (compojure.core/GET "/list" request
      {:body
        (->> (:user/schedules (:current-user request))
             (map util/entity->map)
             (sort-by :title)
             (util/json-write-str))}))

  (compojure.core/context "/schedule" []
    (compojure.core/GET "/create" request
      (friend/authenticated
        (let [user (:current-user request)]
          (views.schedule/schedule-template request {}))))

    (compojure.core/POST "/create" request
      (friend/authenticated
        (let [model (build-model (:params request))]
          (db/create-schedule (:connection request) (:current-user request) model)
          (-> (response/redirect (util/context-uri request "/schedules/all"))
              (assoc :flash {:success "Schedule is created"})))))

    (compojure.core/GET "/:id" [id :as request]
      (friend/authenticated
        (let [id (Long/parseLong id)]
          (check-access! (:current-user request) id)
          (views.schedule/schedule-template request (db/schedule-by-id (:db request) id)))))

    (compojure.core/POST "/:id" [id :as request]
      (friend/authenticated
        (let [id (Long/parseLong id)]
          (check-access! (:current-user request) id)
          (db/update-schedule (:connection request)
                        (-> (build-model (:params request))
                            (assoc :db/id id)))
          (-> (response/redirect (util/context-uri request "/schedules/all")) 
              (assoc :flash {:success "Schedule is updated"})))))

    (compojure.core/DELETE "/:id" [id :as request]
      (friend/authenticated
        (let [id (Long/parseLong id)]
          (check-access! (:current-user request) id)
          (db/delete (:connection request) id)
          {:status 200 :body (util/json-write-str {:id id})})))))
