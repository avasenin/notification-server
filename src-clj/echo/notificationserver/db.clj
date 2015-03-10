(ns echo.notificationserver.db
  (:require
    [clojure.edn :as edn]
    [clojure.tools.logging :as logging]
    [clojure.string :as string]
    [clojure.java.io :as io]
    [datomic.api :as d]
    [echo.notificationserver.core :as core]
    [echo.notificationserver.util :as util])
  (:import [java.util Date])
  (:gen-class))

;; Common functionality

(defn connect
  "Connect to datomic uri. Recreates schema for memory backend by default"
  ([uri] (connect uri (.startsWith ^String uri "datomic:mem:")))
  ([uri recreate?]
    (logging/info "Connect to datomic" uri)
    (when recreate?
      (d/create-database uri))
    (let [conn (d/connect uri)]
      (when recreate?
        (doseq [f (->> (util/find-in-classpath #".*/seed/.*\.tx")
                       (sort-by util/file-name))]
          @(d/transact conn (-> f slurp read-string))))
      conn)))

(defn reset-all
  "lein run -m echo.notificationserver.db/reset-all URI"
  [uri]
  (try
    (d/delete-database uri)
    (connect uri true)
    (finally
      (d/shutdown true))))

(defn qe
  "Return single entity"
  [query db & args]
  (let [eid (ffirst (apply d/q query db args))]
    (d/entity db eid)))

(defn by-attr
  "Lookup entity by attribute"
  [db attr value]
  (qe '[:find ?e
        :in $ ?a ?v
        :where [?e ?a ?v]]
    db attr value))

(defn create
  "Create datomic entity"
  [conn attrs]
  (let [id       (d/tempid :db.part/user)
        attrs'   (assoc attrs :db/id id)
        commit   @(d/transact conn [attrs'])
        db-after (:db-after commit)
        eid      (d/resolve-tempid db-after (:tempids commit) id)]
    (d/entity db-after eid)))

(defn update
  "Update datomic entity"
  [conn attrs]
  (let [db       (d/db conn)
        commit   @(d/transact conn [attrs])
        db-after (:db-after commit)]
    (d/entity db-after (:db/id attrs))))

(defn delete [conn eid]
  @(d/transact conn [[:db.fn/retractEntity eid]]))

;; Custom functions

;; Schedules

(defn schedule-by-id [db id]
  (-> (d/entity db id) util/entity->map))

(defn create-schedule [conn user schedule]
  (let [sid (d/tempid :db.part/user)
        commit @(d/transact conn [(assoc schedule :db/id sid)
                                  {:db/id (:db/id user) :user/schedules sid}])]
    (->> (d/resolve-tempid (:db-after commit) (:tempids commit) sid)
         (d/entity (:db-after commit)))))

(defn update-schedule [conn schedule]
  (let [db (d/db conn)
        sid (:db/id schedule)
        old-connectors (->> (schedule-by-id db sid)
                            :schedule/connectors
                            set)
        new-connectors (->> schedule
                            :schedule/connectors
                            (map keyword) set)
        to-retract (->> (clojure.set/difference old-connectors new-connectors)
                        (map #(vector :db/retract sid :schedule/connectors %)))
        commit @(d/transact conn (concat to-retract [schedule]))]
    (d/entity (:db-after commit) sid)))

;; Tasks

(defn create-task [conn task]
  (let [default-attrs {:task/attempts 0
                       :task/last-at 0
                       :task/status :pending}]
    (create conn (merge default-attrs task))))

(def ^:dynamic *relax-time* 5000)
(def ^:dynamic *max-task-attempts* 5)

(defn next-task [conn]
  (let [checkpoint (System/currentTimeMillis)
        db (d/db conn)
        task (qe '[:find ?e
                   :in $ ?max-attempts ?relax-time ?checkpoint
                   :where [?e :task/attempts ?attempts]
                          [?e :task/last-at ?ts]
                          [?e :task/status ?status]
                          [(!= ?status :done)]
                          [(< ?attempts ?max-attempts)]
                          [(#(< (+ %1 (* %2 %2 %3)) %4) ?ts ?attempts ?relax-time ?checkpoint)]
                   ]
                  db
                  *max-task-attempts* *relax-time* checkpoint)]
    (when task
      (let [attempts (:task/attempts task)
            commit @(d/transact conn [[:db.fn/cas (:db/id task) :task/attempts attempts (inc attempts)]
                                      {:db/id (:db/id task) :task/last-at checkpoint}])]
        (d/entity (:db-after commit) (:db/id task))))))

;; Notifications

(defn create-notification [conn notification]
  (let [timestamp (:notifications/timestamp  notification (java.util.Date.))
        time-attrs {:notifications/timestamp timestamp
                    :notifications/reverse-timestamp (- (.getTime ^java.util.Date timestamp))}]

    (create conn (merge time-attrs notification))))

(defn notifications-after-e [db & [after-e]]
  (let [index (if after-e
                (let [position (get (d/entity db after-e) :notifications/reverse-timestamp)]
                  (->>
                    (d/seek-datoms db :avet :notifications/reverse-timestamp position)
                    (util/after #(= (:e %) after-e))))
                (d/datoms db :avet :notifications/reverse-timestamp))]
    (->> index
      (map :e)
      (map #(d/entity db %)))))

(defn tags [db]
  (->> (d/q '[:find ?v
              :in $ [?attr ...]
              :where [_ ?attr ?v]]
            db
            [:notifications/signature, :notifications/tags])
    seq flatten set))

;; Filters

(defn create-filter [conn user filter]
  (let [sid (d/tempid :db.part/user)
        commit @(d/transact conn [(assoc filter :db/id sid)
                                  {:db/id (:db/id user) :user/filters sid}])]
    (->> (d/resolve-tempid (:db-after commit) (:tempids commit) sid)
         (d/entity (:db-after commit)))))

;; Profiles

(defn profiles [db]
  (->> (d/q '[:find ?id
              :where [?id :user/google-id]] db)
       (map first)
       (map #(d/entity db %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-db [conn]
  (d/db conn))

(defn -main [& opts]
  (binding [*data-readers* {'db/id #'datomic.db/id-literal}]
    (reset-all (get-in (core/config) [:datomic :uri]))))
