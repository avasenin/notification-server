(ns echo.notificationserver.filter-input
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [echo.notificationserver.core :as core]))

;; Parser

(defrecord Token [type text begin end])

(defn- tokenize-values [str]
  (when-not (string/blank? str)
    (->> (re-seq #",+|[^,]+" str)
         (map #(if (re-matches #",+" %) [:comma %] [:value %])))))

(defn- tokenize-filter [filter]
  (let [groups (re-seq #"[^\s]+|\s+" filter)]
    (->>
      (for [group groups]
        (if (re-matches #"\s+" group)
          [[:space group]]
          (if-let [[_ minus key colon values] (re-matches #"(-?)([^:, ]+)(?:(:)([^: ]*))?" group)]
            (concat
             [[:minus minus]
              [:key (str key colon)]]
             (tokenize-values values))
            [[:garbage group]])))
     (mapcat identity))))

(defn- parse-filter [filter]
  (loop [acc []
         pos 0
         tokens (tokenize-filter filter)]
    (if-let [[type text] (first tokens)]
      (let [end (+ pos (.-length text))]
        (if-let [token (case type
                         :key   (Token. :key text pos end)
                         :value (Token. :value text pos end)
                         nil)]
          (recur (conj acc token) end (next tokens))
          (recur acc end (next tokens))))
      acc)))

(defn- test-parse-filter []
  (let [cases ["key:value"  [[:key "key:" 0 4] [:value "value" 4 9]]
               "key:"       [[:key "key:" 0 4]]
               "key"        [[:key "key" 0 3]]
               "key:v1,v2"  [[:key "key:" 0 4] [:value "v1" 4 6] [:value "v2" 7 9]]
               "-key"       [[:key "key" 1 4]]
               "-key:"      [[:key "key:" 1 5]]
               "-key:v1,v2" [[:key "key:" 1 5] [:value "v1" 5 7] [:value "v2" 8 10]]
               "key-1:value-1"         [[:key "key-1:" 0 6] [:value "value-1" 6 13]]
               "key-1:value-1,value-2" [[:key "key-1:" 0 6] [:value "value-1" 6 13] [:value "value-2" 14 21]]

               ;; broken
               ":key"            []
               ":key:"           []
               "key1,key2"       []
               "key1,key2:"      []
               "key:value :key"  [[:key "key:" 0 4] [:value "value" 4 9]]
               "key:value, :key" [[:key "key:" 0 4] [:value "value" 4 9]]
               "key:v1:v2"       []

               "abc:def   -asd:qw-er,,,wer sdf:" [[:key "abc:" 0 4] [:value "def" 4 7]
                                                  [:key "asd:" 11 15] [:value "qw-er" 15 20] [:value "wer" 23 26]
                                                  [:key "sdf:" 27 31]]
               ":a x:y -t:d:,,::f-" [[:key "x:" 3 5] [:value "y" 5 6]]
               ]]
    (doseq [[filter expected] (partition 2 cases)
            :let [actual (parse-filter filter)
                  actual (mapv #(vector (.-type %) (.-text %) (.-begin %) (.-end %)) actual)]]
      (core/test-equals (str "(parse-filter " (pr-str filter) ")") expected actual))))



;; Autocompleter

(defn- before [el coll]
  (take-while #(not= % el) coll))

(defn- after [el coll]
  (->> (drop-while #(not= % el) coll) next))

(defn- only [type coll]
  (filter #(= (.-type %) type) coll))

(defn- only-while [type coll]
  (take-while #(= (.-type %) type) coll))

(defn- locate [text cursor]
  (let [tokens (parse-filter text)]
    (if-let [inside (->> tokens (filter #(<= (.-begin %) cursor (.-end %))) last)]
      (case (.-type inside)
        :value
          (let [key (->> tokens (before inside) (only :key) last)]
            { :inside   inside
              :key      key
              :siblings (->> tokens (after key) (only-while :value)) })
        :key
          (if (and (= (.-end inside) cursor)
                   (core/ends-with? ":" (.-text inside)))
            { :key      inside
              :siblings (->> tokens (after inside) (only-while :value)) }
            { :inside   inside
              :siblings (->> tokens (only :key)) }))
      (or
        (when-let [prev (->> tokens (filter #(< (.-end %) cursor)) not-empty)]
          (let [delta (subs text (.-end (last prev)) cursor)]
            (when (re-matches #",+" delta)
              (let [prev-key (->> prev (only :key) last)]
                { :key      prev-key
                  :siblings (->> tokens (after prev-key) (only-while :value)) }))))
        { :siblings (->> tokens (only :key)) } ))))

(defn- test-locate []
  (let [cases ["key:value" [0 1 2 3] { :inside    (Token. :key "key:" 0 4)
                                       :siblings [(Token. :key "key:" 0 4)] }
               "key:value" [4 5 9]   { :inside    (Token. :value "value" 4 9) 
                                       :siblings [(Token. :value "value" 4 9)]
                                       :key       (Token. :key "key:" 0 4) }
               ["key:" "key: "] 4    { :key       (Token. :key "key:" 0 4)
                                       :siblings  [] }
               "key:,,"    [4 5 6]   { :key       (Token. :key "key:" 0 4)
                                       :siblings  [] }
               "key:value,," [10 11] { :key       (Token. :key "key:" 0 4)
                                       :siblings [(Token. :value "value" 4 9)] }
               "key:,,value,," [4 5] { :key       (Token. :key "key:" 0 4)
                                       :siblings [(Token. :value "value" 6 11)] }
               "key:v1,,v2,,v3" [8 9 10]     { :inside    (Token. :value "v2" 8 10)
                                               :key       (Token. :key "key:" 0 4)
                                               :siblings [(Token. :value "v1" 4 6) (Token. :value "v2" 8 10) (Token. :value "v3" 12 14)] }
               "key:v1,,v2,,v3" [7 11]       { :key       (Token. :key "key:" 0 4)
                                               :siblings [(Token. :value "v1" 4 6) (Token. :value "v2" 8 10) (Token. :value "v3" 12 14)] }
               "k1:v1,v2 k2:v3,v4" [0 1 2]   { :inside    (Token. :key "k1:" 0 3)
                                               :siblings [(Token. :key "k1:" 0 3) (Token. :key "k2:" 9 12)]}
               "k1:v1,v2 k2:v3,v4" [9 10 11] { :inside    (Token. :key "k2:" 9 12)
                                               :siblings [(Token. :key "k1:" 0 3) (Token. :key "k2:" 9 12)]}
               "k1:v1,v2 k2:v3,v4" [3 4 5]   { :inside (Token. :value "v1" 3 5)
                                               :key    (Token. :key "k1:" 0 3)
                                               :siblings [(Token. :value "v1" 3 5) (Token. :value "v2" 6 8)]}
               "k1:v1,v2 k2:v3,v4" [6 7 8]   { :inside (Token. :value "v2" 6 8)
                                               :key    (Token. :key "k1:" 0 3)
                                               :siblings [(Token. :value "v1" 3 5) (Token. :value "v2" 6 8)]}
               "k1:v1,v2 k2:v3,v4" [12 13 14] { :inside (Token. :value "v3" 12 14)
                                                :key    (Token. :key "k2:" 9 12)
                                                :siblings [(Token. :value "v3" 12 14) (Token. :value "v4" 15 17)]}
               "k1:v1,v2 k2:v3,v4" [15 16 17] { :inside (Token. :value "v4" 15 17)
                                                :key    (Token. :key "k2:" 9 12)
                                                :siblings [(Token. :value "v3" 12 14) (Token. :value "v4" 15 17)]}
               "key:, "    [6]                { :siblings [(Token. :key "key:" 0 4)] }
              ]]
    (doseq [[filter cursor expected] (partition 3 cases)
            filter (if (vector? filter) filter [filter])
            cursor (if (vector? cursor) cursor [cursor])]
      (core/test-equals (str "(locate " (pr-str filter) " " cursor ")") expected (locate filter cursor)))))



;; [string] -> (fn [text cursor] -> [[replacement begin end]])
(defn suggest-fn [tags]
  (let [key-value-pairs (map #(let [[_ key value] (re-matches #"([^:]+:)(.*)" %)] [key value]) tags)
        keys            (set (map first key-value-pairs))
        values          (core/multimap key-value-pairs)]
    (fn [text cursor]
      (let [{:keys [inside key siblings]} (locate text cursor)
            siblings (remove #(= % inside) siblings)
            [suggestions prefix begin end] (if key
                                             (if inside
                                               [(get values (.-text key)) (subs text (.-begin inside) cursor) (.-begin inside) (.-end inside)]
                                               [(get values (.-text key)) "" cursor cursor])
                                             (if inside
                                               [keys (subs text (.-begin inside) cursor) (.-begin inside) (.-end inside)]
                                               [keys "" cursor cursor]))
            suggestions (-> 
                          (filter #(core/starts-with? prefix %) suggestions)
                          set
                          (set/difference (set (map :text siblings)))
                          sort)]
        (if (and inside (= suggestions [(.-text inside)]))
          []
          (map #(vector % begin end) suggestions))))))

(defn- test-suggest []
  (let [tags    ["customer:wapo" "customer:cnn" "project:ss-cnn" "project:ss" "source:fbi" "source:pingdom" "source:nagios" "severity:warning"]
        suggest (suggest-fn tags)
        cases   [;; keys suggestions
                 "cust" 4      [["customer:" 0 4]]
                 "cust" 2      [["customer:" 0 4]]
                 "customer"  8 [["customer:" 0 8]]
                 "a:b cust"  8 [["customer:" 4 8]]
                 
                 ;; multiple choices
                 ""      0     [["customer:" 0 0] ["project:" 0 0] ["severity:" 0 0] ["source:" 0 0]]
                 "s"     1     [["severity:" 0 1] ["source:" 0 1]]
                 "a:b "  4     [["customer:" 4 4] ["project:" 4 4] ["severity:" 4 4] ["source:" 4 4]]
                 "a:b s" 5     [["severity:" 4 5] ["source:" 4 5]]
                 "s a:b" 1     [["severity:" 0 1] ["source:" 0 1]]
                 
                 ;; replacing keys
                 "cust" 0      [["customer:" 0 4] ["project:" 0 4] ["severity:" 0 4] ["source:" 0 4]]
                 "sever"  1    [["severity:" 0 5] ["source:" 0 5]]
                 "a:b sever" 5 [["severity:" 4 9] ["source:" 4 9]]
                 "a:b sever" 4 [["customer:" 4 9] ["project:" 4 9] ["severity:" 4 9] ["source:" 4 9]]
                 "sever a:b" 1 [["severity:" 0 5] ["source:" 0 5]]

                 ;; do not suggest key if already used somewhere
                 "customer:wapo " 14 [["project:" 14 14] ["severity:" 14 14] ["source:" 14 14]]

                 ;; do not suggest if only option is what we already have
                 "customer:wapo " 2 []

                 ;; values
                 "customer:"   9 [["cnn" 9 9] ["wapo" 9 9]]
                 "customer:w" 10 [["wapo" 9 10]]
                 "project:s"   9 [["ss" 8 9]  ["ss-cnn" 8 9]]
                 "project:ss" 10 [["ss" 8 10] ["ss-cnn" 8 10]]
                 "customer:wapo project:"     22 [["ss" 22 22] ["ss-cnn" 22 22]]
                 "customer:wapo,cnn project:" 26 [["ss" 26 26] ["ss-cnn" 26 26]]

                 ;; second value, should not repeat already selected
                 "customer:wapo,"      14 [["cnn" 14 14]]
                 "source:pingdom,"     15 [["fbi" 15 15] ["nagios" 15 15]]
                 "source:fbi,pingdom," 19 [["nagios" 19 19]]
                 "customer:wapo  source:pingdom," 30 [["fbi" 30 30] ["nagios" 30 30]]
                 "source:pingdom, customer:wapo"  15 [["fbi" 15 15] ["nagios" 15 15]]

                 ;; replacing value
                 "project:ss" 9 [["ss" 8 10] ["ss-cnn" 8 10]]
                 "project:ss" 8 [["ss" 8 10] ["ss-cnn" 8 10]]
                 "source:fbi,pingdom" 7  [["fbi" 7 10] ["nagios" 7 10]]
                 "source:fbi,pingdom" 11 [["nagios" 11 18] ["pingdom" 11 18]]

                 ;; do not suggest when cannot add anything new
                 "source:fbi"          8 []
                 "customer:wapo,cnn"  17 []
                 "customer:wapo,cnn"  14 []
                 "source:fbi,pingdom"  8 []
                 "source:fbi,pingdom" 12 []
                 ]]
    (doseq [[text pos expected] (partition 3 cases)]
      (core/test-equals (str "(suggest " (pr-str text) " " pos ")") expected (suggest text pos)))))

(comment
  (test-parse-filter)
  (test-locate)
  (test-suggest))
