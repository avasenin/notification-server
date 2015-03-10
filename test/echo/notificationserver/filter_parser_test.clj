(ns echo.notificationserver.filter-parser-test
  (:use [clojure.test])
  (:require
    [echo.notificationserver.filter-parser :as filter-parser]))

(deftest parse-filter-test
  (are [s f] (= f (filter-parser/parse-filter s))
       "project:dataserver tag:facebook"  [{:key "project" :values ["dataserver"] :negative? false}
                                           {:key "tag" :values ["facebook"] :negative? false}]
       " -severity:info,error"            [{:key "severity" :values ["info", "error"] :negative? true}]
       " severity:warning"                [{:key "severity" :values ["warning"] :negative? false}]
       "  project:ds  "                   [{:key "project" :values ["ds"] :negative? false}]
       "project:ss,ds test:firehose,cats" [{:key "project" :values ["ss", "ds"] :negative? false}
                                           {:key "test" :values ["firehose", "cats"] :negative? false}]))

