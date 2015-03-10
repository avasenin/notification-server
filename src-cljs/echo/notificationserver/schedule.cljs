(ns echo.notificationserver.schedule
  (:require
    [dommy.utils :as utils]
    [dommy.core :as dommy]
    cljs.reader
	goog.date
    goog.string.format)
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(defn days [period-select]
  (let [week [[1 "Mon"] [2 "Tue"] [3 "Wed"] [4 "Thu"] [5 "Fri"] [6 "Sat"] [0 "Sun"]]
	today (.getDay (js/Date.))
	week-odd (rem (goog.date/getWeekNumber (.getYear (js/Date.)) (.getMonth (js/Date.)) (.getDate (js/Date.))) (if (= "2w" (.-value period-select)) 2 1))]
    (map #(vector {:class (if (= (first %) (+ today (* week-odd 7))) "cell today" "cell")} (nth % 1))
		(if (= "2w" (.-value period-select)) (concat week (map #(vector (+ (first %) 7) (nth % 1)) week)) week))))

(def ^:dynamic mode false)
		
(defn time-grid [days config]
  (let [dom (node [:table {:class (str "time-grid table table-bordered days-" (count days))}
                     [:thead
                       [:tr
                         [:th {:class "time"} "Hour"]
                         (for [d days]
                           [:th (first d) (nth d 1)])]]
                     [:tbody
                       (for [h (range 24)]
                         [:tr
                           [:td {:class "time"} (goog.string/format "%02d:00" h)]
                           (for [d (range (count days))
                                 :let [checked? (contains? config [d h])
                                       css-class (str "toggler" (when checked? " checked"))]]
                             [:td {:class css-class :day d :hour h}])])]])]

    (dommy/listen! [dom :.toggler]
                   :mousedown #(first [(set! mode (dommy/has-class? (.-selectedTarget %) "checked")) (dommy/toggle-class! (.-selectedTarget %) "checked")])
                   :mouseenter #(when (or (and (not= 0 (.-buttons %)) (= 1 (.-which %))) (and (= 1 (.-buttons %)) (= 1 (.-which %)))) ; left button pressed
                                  (if mode (dommy/remove-class! (.-selectedTarget %) "checked") (dommy/add-class! (.-selectedTarget %) "checked"))))

    dom))

(defn dump-grid-config [container]
  (let [get-attrs (fn [cell]
                    (mapv #(.parseInt js/window (dommy/attr cell %)) ["day" "hour"]))]
  (->> (sel container [:.time-grid :.toggler])
       (filter #(dommy/has-class? % "checked"))
       (map get-attrs) set)))

(defn- onchange-week-period [event]
  (let [target (.-selectedTarget event)
        grid (-> (dommy/closest target :form)
                 (sel1 :.time-grid))
        current-config (dump-grid-config grid)]
    (dommy/replace! grid (time-grid (days target) current-config))))

(defn- onsubmit [event]
  (let [form (.-target event)
        input (node [:input {:type "hidden" :name "schedule/onoff" :value (pr-str (dump-grid-config form))}])]
    (dommy/append! form input)))

(defn render []
  (doseq [container (sel :.schedule-container)]
    (let [form (dommy/closest container :form)
          init-config (cljs.reader/read-string (dommy/attr container "config"))
          grid (time-grid (days (sel1 form :.period)) init-config)]
      (dommy/append! container grid)
      (dommy/listen! [form :select.period] :change onchange-week-period)
      (dommy/listen! form :submit onsubmit))))
