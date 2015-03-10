(ns echo.notificationserver.log
  (:require
    [clojure.string :as string]
    [dommy.core :as dommy]
    [dommy.attrs :as attrs]
    [dommy.utils :as utils]
    moment
    [echo.notificationserver.core :as core]
    [echo.notificationserver.autocomplete :as autocomplete]
    [echo.notificationserver.filter-input :as filter-input]
    [echo.notificationserver.infiniscroll :as infiniscroll])
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(def last-id (atom nil))
(def suggest-fn (atom nil))
(def ^:const page-size "25")

(defn render-tags [ts]
  (map #(vector :span.tag %) (sort ts)))

(defn render-badge [n]
  (let [classes (for [tag (concat (:notifications/signature n) (:notifications/tags n))]
                  (str "badge_" (string/replace tag #":" "_")))]
    [:span.badge_icon {:class (string/join " " classes)} "■ "]))

(defn render-time [s]
  (let [t (js/moment s)]
    [:span
      {:title s}
      (.format t "MMM D, HH:mm:ss")]))

(deftemplate row [n]
  [:tr
    [:td (render-badge n)
         (render-time (n :notifications/timestamp))]
    [:td [:strong (n :short-message) [:br]] [:small.text-muted.notification__message (n :notifications/message)]]
    [:td (render-tags (n :notifications/signature))]
    [:td (render-tags (n :notifications/tags))]])

(defn render-table [ns]
  (let [tbody (sel1 :#notifications)]
    (if (empty? ns)
      (dommy/append! tbody [:tr [:td {:colspan 6} [:span.text-danger "Nothing found bro"]]])
      (doseq [n ns]
        (dommy/append! tbody (row n))))
    (reset! last-id (:id (last ns)))))

(defn query-events [q]
  (core/q :get "/api/log" {:q q :amount page-size}
    (fn [{ns :data}]
      (dommy/clear! (sel1 :#notifications))
      (render-table ns)
      (reset! infiniscroll/loading? false))))

(defn setup-infiniscroll []
  (infiniscroll/setup (sel1 :.footer) (fn []
    (core/q :get "/api/log" {:q       (dommy/value (sel1 :#filter))
                             :after   @last-id
                             :amount  page-size}
      (fn [{ns :data}]
        (when-not (empty? ns)
          (render-table ns)
          (reset! infiniscroll/loading? false)))))))

(defn notifications-log []
  (query-events (dommy/value (sel1 :#filter)))
  (core/listen-onchange (sel1 :#filter) 300 query-events)
  (autocomplete/setup (sel1 :.dropdown) #((or @suggest-fn (constantly [])) %1 %2))
  (when-not @suggest-fn
    (core/q :get "/api/tags" (fn [tags]
      (reset! suggest-fn (filter-input/suggest-fn tags))))))

(defn render []
  (.focus (sel1 :#filter))
  (notifications-log)
  (setup-infiniscroll))
