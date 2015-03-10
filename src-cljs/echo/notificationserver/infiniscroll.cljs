(ns echo.notificationserver.infiniscroll
  (:require
    [dommy.core :as dommy]
    [dommy.attrs :as attrs])
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(def loading? (atom false))

(defn bottom? [footer-el]
  (let [top  (.-top (attrs/bounding-client-rect footer-el))]
    (< top js/window.innerHeight)))

(defn setup [footer-el on-bottom]
  (dommy/listen! js/window :scroll
    (fn [_]
      (when (and (not @loading?)
                 (bottom? footer-el))
        (reset! loading? true)
        (on-bottom)))))
