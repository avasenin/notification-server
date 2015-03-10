(ns echo.notificationserver.views.profile
  (:require
    [echo.notificationserver.views.layouts :as layouts]
    [echo.notificationserver.util :as util]))

(defn update [request model]
  (let [params (:params request)]
  (layouts/default request "Profile"
    ""
    [:form {:action (:uri request) :method "POST" :class "form-horizontal" :role "form"}
      [:div.form-group
        [:label.col-sm-2.control-label {:for "name"} "Name"]
        [:div.col-sm-6
          [:input {:id "name" :class "form-control" :name "user/name" :value (:user/name model)}]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "email"} "Email"]
        [:div.col-sm-6
          [:input {:type "email" :id "email" :class "form-control" :name "user/email" :value (:user/email model)}]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "phone"} "Phone"]
        [:div.col-sm-6
          [:input {:type "phone" :id "phone" :class "form-control" :name "user/phone" :value (:user/phone model)}]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "pushover"} "Pushover key"]
        [:div.col-sm-6
          [:input {:type "phone" :id "pushover" :class "form-control" :name "user/pushover" :value (:user/pushover model)}]]]

      [:div.form-group
        [:label.col-sm-2.control-label {:for "http"} "HTTP URL"]
        [:div.col-sm-6
          [:input {:type "http" :id "http" :class "form-control" :name "user/http" :value (:user/http model)}]]]

      [:div.form-group
        [:div.col-sm-offset-2.col-sm-4
          [:input {:class "btn btn-primary" :type "submit"}]]]])))
