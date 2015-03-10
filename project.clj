(defproject notificationserver "0.1.0"
  :global-vars    {*warn-on-reflection* true}
  :source-paths   ["src-clj"]
  :resource-paths ["resources"]
  :dependencies [
    [org.clojure/clojure "1.6.0"]

    [org.clojure/data.json "0.2.3"]
    [org.clojure/tools.logging "0.2.6"]
    [ch.qos.logback/logback-classic "1.0.13"]
    [clj-http "0.7.7" :exclusions [cheshire crouton org.clojure/tools.reader]]
    [clj-time "0.7.0"]

    [ring/ring-core "1.2.1" :exclusions [org.clojure/tools.reader]]
    [ring/ring-devel "1.2.1"]
    [ring/ring-jetty-adapter "1.1.8"]
    [compojure "1.1.6"]
    [hiccup "1.0.4"]
    [com.cemerick/friend "0.2.0"]
    [jarohen/nomad "0.6.4"]

    [com.draines/postal "1.11.1"]
    [javax.activation/activation "1.1.1"]
    [javax.mail/javax.mail-api "1.5.1"]

    [com.novemberain/langohr "2.3.2"]
    [com.taoensso/nippy "1.1.0"]

    [org.clojure/clojurescript "0.0-2075"]
    [prismatic/dommy "0.1.2" :exclusions [crate prismatic/cljs-test]]

    [com.datomic/datomic-pro "0.9.4384" :exclusions [org.slf4j/jcl-over-slf4j org.slf4j/jul-to-slf4j org.slf4j/log4j-over-slf4j org.slf4j/slf4j-nop]]
  ]

  :plugins [
    [lein-cljsbuild "1.0.0"]
    [lein-ring "0.8.8"]
  ]

  :main echo.notificationserver.server
  :aot [echo.notificationserver.server echo.notificationserver.db]
  :uberjar-name "ns.jar"

  ; lein ring server-headless 8000
  :ring { :handler echo.notificationserver.server/app }
  :profiles {
    :dev {
      :ring { :handler echo.notificationserver.server/app-dev }
      :main echo.notificationserver.server
      :cljsbuild {:builds [
        { :id "dev"
          :source-paths ["src-cljs"]
          :compiler {
            :output-to "resources/static/js/ns.dev.js"
            :optimizations :whitespace
            :prety-print true
            :foreign-libs [{:file "resources/static/js/moment.min.js"
                            :provides ["moment"]}]
          }
        }
      ]}
    }
  }
  :cljsbuild {:builds [
    { :id "prod"
      :source-paths ["src-cljs"]
      :compiler {
        :output-to "resources/static/js/ns.min.js"
        :optimizations :whitespace
        :pretty-print false
        :foreign-libs [{:file "resources/static/js/moment.min.js"
                        :provides ["moment"]}]
      }
    }
  ]}
  ;; add datomic repository here
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
)
