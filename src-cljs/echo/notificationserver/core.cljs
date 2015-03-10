(ns echo.notificationserver.core
  (:require
    [clojure.string :as string]
    [dommy.core :as dommy]
    [dommy.attrs :as attrs]
    [dommy.utils :as utils]
    [echo.notificationserver.schedule :as schedule]
    goog.net.XhrManager
    goog.net.XhrIo)
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(enable-console-print!)

(defn starts-with? [prefix s]
  (= prefix (subs s 0 (.-length prefix))))

(defn ends-with? [suffix s]
  (= suffix (subs s (- (.-length s) (.-length suffix)))))

(defn last-index-of [pred coll]
  (loop [idx (dec (count coll))]
    (cond
      (neg? idx)        -1
      (pred (coll idx)) idx
      :else             (recur (dec idx)))))

(defn multimap [pairs]
  (reduce (fn [acc [k v]] (assoc acc k (conj (get acc k #{}) v))) {} pairs))

(defn test-equals [msg expected actual]
  (if (not= actual expected)
    (.warn js/console (str msg ", expected " (pr-str expected) ", got " (pr-str actual)))
    (.debug js/console msg)))

(defn by-id [id]
  (js/document.getElementById id))

(set! (.-FORM_CONTENT_TYPE goog.net.XhrIo) "application/json;charset=utf-8")
(def ^:private xhr-manager (goog.net.XhrManager. nil nil nil 0 15000))
(defn q
  "Sends an XHR, returns parsed JSON response.
   Usage:
    (core/q :get \"/api/log\" {:q \"abc\"} callback)"
  ([method url callback] (q method url {} nil callback))
  ([method url query callback] (q method url query nil callback))
  ([method url query body callback]
    (.send xhr-manager
      (str (rand))
      (->> query
        (map (fn [[k v]] (str (js/encodeURIComponent (name k)) "=" (js/encodeURIComponent v))))
        (string/join "&")
        (str url "?"))
      (name method)
      (when body (JSON/stringify (clj->js body)))
      (clj->js {"Content-type" "application/json;charset=UTF-8"})
      nil
      (fn [e] 
        (let [resp (.-target e)]
          (if (.isSuccess resp)
            (-> (.parse js/JSON (.getResponse resp))
              (js->clj :keywordize-keys true)
              callback)
            (println "ERROR loading" (.getResponse resp)))))
      nil
      nil)))


(defn listen-onchange
  "Listen for element's value change (usually an input),
   throttling `change` events with timeout"
  [el timeout callback]
  (let [timer (atom nil)
        last  (atom nil)
        react (fn []
                (when-let [timer @timer]
                  (js/clearTimeout timer))
                (reset! timer
                  (js/setTimeout
                    (fn []
                      (let [value (dommy/value el)]
                        (when (not= @last value)
                          (callback value)
                          (reset! last value))
                        (reset! timer nil)))
                    timeout)))]
    (dommy/listen! el
      :keydown react
      :change  react )))


