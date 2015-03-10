(ns echo.notificationserver.views.log
  (:require
    [echo.notificationserver.views.layouts :as layouts]
    [echo.notificationserver.util :as util]))

(defn log [request]
  (layouts/default request "Notifications"
    "echo.notificationserver.log.render();"
    [:div
      [:table.table.table-hover.table-condensed
        [:thead
          [:tr
            [:th "Time"] [:th "Message"] [:th "Signature"] [:th "Tags"]]
          [:tr
            [:td {:colspan "6"}
              [:div.dropdown
                [:input#filter.form-control.dropdown-toggle {:type "search" :placeholder "Filter" :autocomplete "off"}]]]]]
        [:tbody#notifications]]]))
