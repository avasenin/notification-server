(ns echo.notificationserver.autocomplete
  (:require
    [clojure.string :as string]
    [dommy.core :as dommy]
    [dommy.attrs :as attrs]
    [dommy.utils :as utils]
    [echo.notificationserver.core :as core])
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(defn hide [el-container]
  (dommy/remove-class! el-container "open")
  (attrs/remove-attr! el-container "data-suggestions"))

(defn redraw-suggestions [el-container suggestions-fn]
  (let [el-input    (sel1 el-container :input)
        value       (dommy/value el-input)
        cursor      (.-selectionEnd el-input)
        el-dropdown (sel1 el-container :ul.dropdown-menu)]
    (if-let [suggestions (not-empty (suggestions-fn value cursor))]
      (do
        (let [[_ begin _] (first suggestions)]
          (dommy/set-text! (sel1 el-container :.width) (subs value 0 begin))
          (attrs/set-px! el-dropdown "left" (.-clientWidth (sel1 el-container :.width))))
        (dommy/add-class! el-container "open")
        (when (not= (attrs/attr el-container "data-suggestions") (str suggestions))
          (dommy/replace-contents! el-dropdown (for [s suggestions] [:li {:data-suggestion (pr-str s)} [:a (first s)]]))
          (attrs/add-class! (first (sel el-dropdown :li)) "active")
          (attrs/set-attr! el-container "data-suggestions" (str suggestions))))
      (hide el-container))))

(defn select-next [el-container]
  (let [el-ul (sel1 el-container :ul)]
    (if-let [active (sel1 el-ul :li.active)]
      (do
        (dommy/remove-class! active "active")
        (when-let [next (.-nextElementSibling active)]
          (dommy/add-class! next "active")))
      (when-let [first (first (sel el-ul :li))]
        (dommy/add-class! first "active")))))

(defn select-prev [el-container]
  (let [el-ul (sel1 el-container :ul)]
    (if-let [active (sel1 el-ul :li.active)]
      (do
        (dommy/remove-class! active "active")
        (when-let [next (.-previousElementSibling active)]
          (dommy/add-class! next "active")))
      (when-let [first (last (sel el-ul :li))]
        (dommy/add-class! first "active")))))

(defn complete [el-container el-active]
  (let [el-input        (sel1 el-container :input)
        cursor          (.-selectionEnd el-input)
        old             (dommy/value el-input)
        [new begin end] (cljs.reader/read-string (dommy/attr el-active :data-suggestion))
        replaced        (str (subs old 0 begin) new (subs old end))]
    (dommy/set-value! el-input replaced)
    (dommy/fire! el-input :change)
    (set! (.-selectionStart el-input) (+ begin (.-length new)))
    (set! (.-selectionEnd el-input) (+ begin (.-length new)))
    true))

(defn mouse-select [el-container el-li]
  (complete el-container (dommy/text (sel1 el-li :a))))

(defn setup [el-container suggestions-fn]
  (dommy/append! el-container [:div.width.form-control])
  (dommy/append! el-container [:ul.dropdown-menu])
  (let [el-input     (sel1 el-container :input)
        el-dropdown  (sel1 el-container :.dropdown-menu)
        blur-timeout (atom nil)]
    (dommy/listen! el-input :blur  (fn [_] (reset! blur-timeout (js/setTimeout #(hide el-container) 10))))
    (dommy/listen! el-input :focus (fn [_] (js/clearTimeout @blur-timeout) (redraw-suggestions el-container suggestions-fn)))
    (dommy/listen! el-input :click (fn [_] (redraw-suggestions el-container suggestions-fn)))
    (dommy/listen! [el-dropdown :li] :click
      (fn [e]
        (.preventDefault e)
        (mouse-select el-container (.-selectedTarget e))
        (redraw-suggestions el-container suggestions-fn)))
    (dommy/listen! el-input :keydown
      (fn [e]
        (let [keycode (.-keyCode e)
              active  (sel1 el-container [:ul :li.active])]
          (if (or (not (attrs/has-class? el-container "open"))
                  (= :default (case (.-keyCode e)
                                38 (select-prev el-container) ;; ↑
                                40 (select-next el-container) ;; ↓
                                13 (when (and active          ;; enter
                                              (complete el-container active))    
                                     (redraw-suggestions el-container suggestions-fn))
                                9  (when (and active          ;; tab
                                              (complete el-container active))    
                                     (redraw-suggestions el-container suggestions-fn))
                                27 (hide el-container)        ;; esc
                                :default)))
            (js/setTimeout (partial redraw-suggestions el-container suggestions-fn) 10)
            (.preventDefault e))
          )))))

