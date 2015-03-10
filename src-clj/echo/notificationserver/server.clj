(ns echo.notificationserver.server
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [ring.middleware.reload :as reload]
    [ring.middleware.flash :as flash]
    [ring.util.response :as response]
    [ring.adapter.jetty :as jetty]
    [compojure core handler route]
    [cemerick.friend :as friend]
    [cemerick.friend.openid :as openid]
    [echo.notificationserver.middleware :as middleware]
    [echo.notificationserver.db :as db]
    [echo.notificationserver.core :as core]
    [echo.notificationserver.mailman :as mailman]
    [echo.notificationserver.filter-parser :as filter-parser]
    [echo.notificationserver.router :as router]
    [echo.notificationserver.util :as util]
    [echo.notificationserver.controllers.schedule :as controllers.schedule]
    [echo.notificationserver.controllers.filter :as controllers.filter]
    [echo.notificationserver.views.log :as log]
    [echo.notificationserver.views.profile :as profile]
    [echo.notificationserver.views.layouts :as layouts])
  (:gen-class))

(compojure.core/defroutes api

  (compojure.core/POST "/notify" [:as req]
    (let [raw-n (-> (:body req)
                    util/json-read-str
                    (util/qualify-keys :notifications))]
      (logging/info raw-n)
      (if (core/notification-valid? raw-n)
        (do
          (try
            (mailman/store (:connection req) raw-n)
            (catch Exception ex
              (logging/error "Failed to deliver notification" raw-n "due to" ex)
              {:status 500
               :headers {"Content-Type" "text/plain"}
               :body {:message "Failed to store notification"}}))
          {:status 201
           :headers {"Content-Type" "text/plain"}
           :body {:message "Stored"}})
        {:status 400
         :headers {"Content-Type" "text/plain"}
         :body {:message "Invalid payload"}})))

  (compojure.core/GET  "/log"    [:as req]
    (let [{:keys [q after amount]} (:params req)
          after-e (util/parse-long after)]
      {:body
        {:data (if-let [f (filter-parser/parse-filter q)]
                  (->>
                    (db/notifications-after-e (:db req) after-e)
                    (map util/entity->map)
                    (filter #(router/satisfies-filter? f %))
                    (take (or (util/parse-long amount) 25)))
                  [])
         :q q}}))

  (compojure.core/GET  "/tags" [:as req]
    {:body (db/tags (:db req))}))

(compojure.core/defroutes site

  controllers.schedule/routes
  controllers.filter/routes

  (compojure.core/GET "/" request
    (if-let [current-user (friend/current-authentication)]
      (ring.util.response/redirect (util/context-uri request "/schedules/all"))
      (layouts/main request)))

  (compojure.core/GET "/logout" request
    (friend/logout* (response/redirect (util/context-uri request "/"))))

  ;; Profile processing
  (compojure.core/GET "/profile" request
    (friend/authenticated
      (let [user (:current-user request)]
        (profile/update request user))))

  (compojure.core/POST "/profile" request
    (friend/authenticated
      (logging/info (:params request))
      (let [user (-> (util/extract-attrs [:user] (:params request))
                     (assoc :db/id (:db/id (:current-user request))))]
        (db/update (:connection request) user)
        (-> (response/redirect (util/context-uri request "/profile"))
            (assoc :flash {:success "Profile is saved"})))))

  (compojure.core/GET "/log" request
    (friend/authenticated
      (log/log request))))

(defonce connection (delay (db/connect (get-in (core/config) [:datomic :uri]))))

(compojure.core/defroutes app
  (->
    (compojure.core/routes
      (compojure.route/resources "/" { :root "static" })
      (compojure.core/GET "/health" [] "OK\n")
      (compojure.core/context "/api" []
        (-> api
          middleware/wrap-json
          (middleware/set-db connection)
          compojure.handler/api))
      (-> site
        (middleware/set-db connection)
        (middleware/initialize-user-if-needed connection)
  ;      (middleware/force-login {:email "vaseninx@aboutecho.com" :firstname "Andrey" :lastname "Vasenin"}) ; for debug puproses
        (middleware/authenticate)
        (compojure.handler/site)))
    middleware/wrap-errors))

(def app-dev (reload/wrap-reload app ["src-clj"]))

(defn -main [& opts]
  (mailman/run-workers @connection)
  (jetty/run-jetty app-dev {:port 8080}))
