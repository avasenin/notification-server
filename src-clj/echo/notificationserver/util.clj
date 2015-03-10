(ns echo.notificationserver.util
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.data.json :as json])
  (:import java.net.URI
           com.google.common.reflect.ClassPath))

(defn filter-keys [f m]
  (filter (fn [[k v]] (f k)) m))

(defn map-keys [f m]
  (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn ^java.text.SimpleDateFormat dateformat
  ([] (dateformat "yyyy-MM-dd'T'HH:mm:ss'Z'"))
  ([^String format]
    (doto
      (java.text.SimpleDateFormat. format (java.util.Locale. "en"))
      (.setTimeZone (java.util.TimeZone/getTimeZone "GMT")))))

(defn after [pred coll]
  (->> coll (drop-while #(not (pred %))) next))

(defn parse-long [str]
  (when str
    (Long/parseLong str)))

(defn json-writer [k v]
  (cond
    (instance? java.util.Date v) (.format (dateformat) v)
    :else v))

(defn key-fn [k]
  (str
    (some-> (namespace k) (str "/"))
    (name k)))

(defn raise+
  ([data]
    (raise+ (pr-str data) data))
  ([message data]
    (throw (ex-info message data))))

(defn json-write-str [obj]
  (json/write-str obj :escape-slash false :key-fn key-fn :value-fn json-writer))

(defn json-read-str [str]
  (json/read (io/reader str) :key-fn keyword))

(defn resolve-uri
  [context ^String uri]
  (let [^URI context (if (instance? URI context) context (URI. context))]
    (.resolve context uri)))

(defn context-uri
  "Resolves a [uri] against the :context URI (if found) in the provided
   Ring request.  (Only useful in conjunction with compojure.core/context.)"
  [{:keys [context]} uri & args]
  (if-let [base (and context (str context "/"))]
    (str (resolve-uri base (apply str uri args)))
    (apply str uri args)))

(defn find-in-classpath
  "Returns list of java.net.URLs for files that are at classpath and match `re`.
   Searches in jar files too."
  [re]
  (let [classpath (ClassPath/from (.getClassLoader clojure.lang.PersistentVector))]
    (->> (.getResources classpath)
      (filter #(re-matches re (.getPath (.url %))))
      (mapv #(.url %)))))

(defn file-name [url]
  (-> url (.getPath) (string/split #"/") last))

(defn extract-attrs [ns coll]
  (let [s (set (map name ns))]
    (->> coll
      (filter (fn [[k v]] (get s (namespace (keyword k)))))
      (map (fn [[k v]] [(keyword k) v]))
      (into {}))))

(defn qualify-keys
  "Converts all keys from :name to :tns/name"
  [m ns]
  (map-keys (comp #(keyword (name ns) %) name) m))

(defn entity->map [e]
  (into {:db/id (:db/id e)} e))

(defn find-first [k v coll]
  (->> coll (filter #(= v (k %))) first))
