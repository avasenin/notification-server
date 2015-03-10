(ns echo.notificationserver.controllers.filter
  (:require
    [clojure.string :as string]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [ring.util.response :as response]
    [compojure core handler route]
    [cemerick.friend :as friend]
    [echo.notificationserver.db :as db]
    [echo.notificationserver.util :as util]
    [echo.notificationserver.views.layouts :as layouts]))

(defn check-access! [user id]
  (when-not (some->> (:user/filters user)
                     (util/find-first :db/id id))
    (util/raise+ {:message "Can't find id in user scope" :code 404})))

(compojure.core/defroutes routes
  (compojure.core/context "/filters" []
    (compojure.core/GET "/" request
      (friend/authenticated 
        (layouts/default request "Filters" "echo.notificationserver.filters.render_filters();" nil)))

    (compojure.core/GET "/list" [:as request]
      {:body (->> (:user/filters (:current-user request))
                  (map util/entity->map)
                  (sort-by :value)
                  (util/json-write-str))})

    (compojure.core/PUT "/:id" [id :as request]
      (friend/authenticated
        (let [{value :value} (util/json-read-str (:body request))
              id   (util/parse-long id)]
          (check-access! (:current-user request) id)
          (db/update (:connection request) {:db/id id :filter/value value})
          {:status 200 :body (util/json-write-str {:id id})})))

    (compojure.core/POST "/" [:as request]
      (friend/authenticated
        (let [{value :value} (util/json-read-str (:body request))
              model (db/create-filter (:connection request) (:current-user request) {:filter/value value})]
          {:status 201 :body (util/json-write-str {:id (:db/id model)})})))

    (compojure.core/DELETE "/:id" [id :as request]
      (friend/authenticated
        (let [id (util/parse-long id)]
          (check-access! (:current-user request) id)
          (db/delete (:connection request) id)
          { :status 200
            :body (util/json-write-str {:id id})})))))
