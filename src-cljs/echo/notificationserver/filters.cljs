(ns echo.notificationserver.filters
  (:require
    [clojure.string :as string]
    [dommy.core :as dommy]
    [dommy.attrs :as attrs]
    [dommy.utils :as utils]
    moment
    [echo.notificationserver.core :as core]
    [echo.notificationserver.log :as log])
  (:use-macros
    [dommy.macros :only [node sel sel1 deftemplate]]))

(declare render-preview)

(deftemplate render-filter [filter]
  (.log js/console (pr-str filter))
  [:li.refli.filters__li {:id (:db/id filter) :data-filter (pr-str filter)}
    [:span.delete.glyphicon.glyphicon-remove {:title "Remove this filter"}]
    [:span.title.ref.filters__filter (:filter/value filter)]])

(deftemplate render-filter-deleted [filter]
  [:li.refli.filters__li {:id (:db/id filter) :data-filter (pr-str filter)}
    [:span.title.filters__filter_deleted (:filter/value filter)]
    [:em.flash [:span "Deleted. " [:a.undelete {:href "#"} "Undo"]]]])

(deftemplate render-filters-list [filters]
  [:div
    [:h1 "Filters"]
    [:ul.reflist.filters
      (concat
        (map render-filter filters)
        [[:li.refli.filters__li
           [:span.title.ref.filters__filter "Add new filter"]]])]])

(defn delete-filter [filter]
  (core/q :delete (str "/filters/" (:db/id filter))
    (fn [_]
      (dommy/replace! (core/by-id (:db/id filter)) (render-filter-deleted filter)))))

(defn undelete-filter [filter]
  (core/q :post "/filters" {} (dissoc filter :db/id)
    (fn [{id :id}]
      (dommy/replace! (core/by-id (:db/id filter)) (render-filter (assoc filter :db/id id)))
      (dommy/append! (core/by-id id) [:em.flash "Restored"]))))

(defn find-filter [el]
  (when-let [el-filter (dommy/closest el "[data-filter]")]
    (cljs.reader/read-string (attrs/attr el-filter "data-filter"))))

(defn render-filters
  ([] (render-filters nil))
  ([highlight]
    (core/q :get "/filters/list"
      (fn [filters] (render-filters filters highlight))))
  ([filters highlight]
    (dommy/replace-contents! (sel1 :.container) (render-filters-list filters))
    (dommy/append! (core/by-id (:id highlight)) [:em.flash (:message highlight)])
    (dommy/listen! [(sel1 :.filters) :.delete] :click
      (fn [e] (delete-filter (find-filter (.-target e)))))
    (dommy/listen! [(sel1 :.filters) :.undelete] :click
      (fn [e]
        (.preventDefault e)
        (undelete-filter (find-filter (.-target e)))))
    (dommy/listen! [(sel1 :.filters) :.filters__filter] :click
      (fn [e] (render-preview (find-filter (.-target e)))))))


(deftemplate preview [filter]
  [:div
    [:h1 (if filter "Filters / Editing" "Filters / Adding")]
    [:div
      [:div.form-group.dropdown
        [:input#filter.form-control.dropdown-toggle {:type "text" :placeholder "Filter" :value (:filter/value filter) :autocomplete "off"}]]
      [:div.form-group
        [:button#save.btn.btn-default {:disabled true} (if filter "Save & Close" "Add & close")]
        [:span.or " or just "]
        [:button#close.btn.btn-link "Close"]]]
    [:h3 "Preview"]
    [:table.table.table-hover.table-condensed
      [:thead
        [:tr
          [:th "Time"] [:th "Message"] [:th "Signature"] [:th "Tags"]]]
      [:tbody#notifications]]])

(defn add [new-filter callback]
  (core/q :post "/filters" {} new-filter
    (fn [data] (callback {:id (:id data) :message "Added!"}))))

(defn save [new-filter callback]
  (core/q :put (str "/filters/" (:id new-filter)) {} new-filter
    (fn [_] (callback {:id (:id new-filter) :message "Saved!"}))))

(defn render-preview [old-filter]
  (dommy/replace-contents! (sel1 :.container) (preview old-filter))
  (let [el-input (sel1 :#filter)
        collect-new-filter #(merge old-filter {:value (dommy/value el-input)})]
    (.focus el-input)
    (set! (.-selectionStart el-input) (.-length (dommy/value el-input)))
    (log/notifications-log)
    (dommy/listen! (sel1 :#save) :click (fn [_]
      (dommy/set-text! (sel1 :#save) "Saving...")
      (if old-filter
        (save (collect-new-filter) render-filters)
        (add  (collect-new-filter) render-filters))))
    (dommy/listen! (sel1 :#close) :click (fn [_]
      (render-filters)))
    (core/listen-onchange el-input 100 (fn [_] 
      (dommy/toggle-attr! (sel1 :#save) :disabled (= old-filter (collect-new-filter)))))))

