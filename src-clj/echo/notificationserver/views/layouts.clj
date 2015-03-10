(ns echo.notificationserver.views.layouts
  (:require
    [cemerick.friend :as friend]
    [hiccup.form :as form]
    [hiccup.page :as page]
    [hiccup.core :as hiccup]
    [echo.notificationserver.core :as core]
    [echo.notificationserver.util :as util]))


(defn- head [title]
  [:head
    [:title title]
    [:link {:href "/favicon.ico" :rel "shortcut icon" :type "image/x-icon"}]
    (page/include-css "/css/bootstrap.min.css")
    (page/include-css "/css/ns.css")])

(defn- foot []
  [:div.footer
    [:div.container
      [:p "Echo / Platform Engineering East Team / 2014"]]])

(defn- authentication-form [request content]
  [:form {:method "POST" :action (util/context-uri request "/login")}
    [:input {:type "hidden" :name "identifier" :value "https://www.google.com/accounts/o8/id"}]
    content])

(defn nav-tab [req url title]
  [:li
    {:class (if (= url (:uri req)) "active" "")}
    [:a
      {:href (util/context-uri req url)}
      title]])

(defn- navigation [request]
  [:nav {:class "navbar navbar-default" :role "navigation"}
   [:div {:class "navbar-header"}
     [:a {:class "navbar-brand" :href (util/context-uri request "/")} "Echo"]]
   [:div {:class "collapse navbar-collapse"}
     [:ul {:class "nav navbar-nav"}
        (nav-tab request "/schedules/all" "Schedules")
        (nav-tab request "/filters" "Filters")
        (nav-tab request "/log" "Notifications")
        (nav-tab request "/profile" "Profile")]
     [:p {:class "navbar-text pull-right"}
        [:span {:class "glyphicon glyphicon-user"}]
        "&nbsp;" (:email (friend/current-authentication)) "&nbsp;&nbsp;"
        [:a {:href (util/context-uri request "/logout") :class "btn btn-xs btn-link"}
          "Log out"]]]])

(defn alerts [request]
  (for [[class text] (:flash request)]
    [:div {:class (str "alert alert-" (name class))} text]))

(defn default [request title js & content]
  (page/html5
    (head title)
    [:body
      (navigation request)
      [:div.container.content
        (alerts request)
        content]
      (foot)
      (page/include-js (:js (core/config)))
      [:script js]]))

(defn main [request]
  (page/html5
    (head "Echo Notification Server")
    [:body.login_page
      [:div.container
        [:div.login_page__logo]
        [:h1 "Echo Notification Server"
          [:br]
          [:small "We keep you up at night"]]
        [:p (authentication-form request
          [:button.btn.btn-primary {:type "submit"}
            [:i {:class "glyphicon glyphicon-user"}] " Login"])]]
      (foot)]))
