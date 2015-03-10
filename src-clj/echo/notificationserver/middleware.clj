(ns echo.notificationserver.middleware
  (:require
    [clojure.tools.logging :as logging]
    [clojure.string :as string]
    [cemerick.friend :as friend]
    [cemerick.friend.workflows :as workflows]
    [cemerick.friend.openid :as openid]
    [echo.notificationserver.db :as db]
    [echo.notificationserver.util :as util]))


(defn wrap-json [app]
  (fn [req]
    (-> (app req)
      (update-in [:body] util/json-write-str)
      (update-in [:headers] assoc "Content-Type" "application/json; charset=UTF-8"))))

(defn wrap-errors [app]
  (fn [req]
    (try
      (app req)
      (catch Exception e
        (logging/error e "Error handling request" req)
        { :status  500
          :headers {"Content-Type" "text/plain"}
          :body    (.getMessage e) }))))

(defn force-login [ring-handler auth]
  (fn [req]
    (binding [friend/*identity* {:current ::identity, :authentications {::identity auth}}]
      (ring-handler req))))

(defn initialize-user-if-needed [ring-handler conn]
  (fn [req]
    (-> req
      (assoc :current-user
             (when-let [{:keys [email firstname lastname]} (friend/current-authentication)]
               (or (db/by-attr (db/get-db @conn) :user/google-id email)
                   (db/create @conn {:user/name (->> [firstname lastname]
                                                     (remove string/blank?)
                                                     (string/join " "))
                                     :user/email email
                                     :user/google-id email}))))
      ring-handler)))

(defn set-db [ring-handler conn]
  (fn [req]
    (-> req
      (assoc :db (db/get-db @conn) :connection @conn)
      ring-handler)))

(defn authenticate [ring-handler]
   (friend/authenticate ring-handler
     {:allow-anon? true
      :default-landing-uri "/"
      :login-uri "/"
      :workflows [(openid/workflow
                    :openid-uri "/login"
                    :credential-fn identity)]}))
