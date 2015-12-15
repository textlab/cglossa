(defproject cglossa "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170" :scope "provided"]
                 [org.clojure/core.async "0.2.371"]
                 [ring "1.3.2"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [com.cognitect/transit-clj "0.8.275"]
                 [compojure "1.3.4"]
                 [enlive "1.1.5"]
                 [reagent "0.6.0-SNAPSHOT"]
                 [cljsjs/jquery "1.9.0-0"]
                 [cljsjs/jquery-ui "1.11.3-1"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]
                 [cljs-http "0.1.35"]
                 [prone "0.8.2"]
                 [korma "0.4.2"]
                 [mysql/mysql-connector-java "5.1.37"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/data.avl "0.0.12"]
                 [me.raynes/conch "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [cheshire "5.5.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [binaryage/devtools "0.3.0"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.0"]
            [lein-sassc "0.10.0"]
            [lein-auto "0.1.1"]]

  :min-lein-version "2.5.0"

  :uberjar-name "cglossa.jar"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :main cglossa.server

  :clean-targets ^{:protect false} ["resources/public/js/out"]

  :cljsbuild
  {:builds
   {:app
    {:source-paths ["src/cljs"]
     :compiler     {:output-to            "resources/public/js/out/app.js"
                    :output-dir           "resources/public/js/out"
                    :source-map           "resources/public/js/out/out.js.map"
                    :source-map-timestamp true
                    :optimizations        :none
                    :cache-analysis       true
                    :recompile-dependents false
                    :main                 "cglossa.core"
                    :asset-path           "js/out"
                    :foreign-libs         [{:file     "resources/public/js/react-bootstrap_0.28.1.min.js"
                                            :provides ["react-bootstrap"]
                                            :requires ["cljsjs.react"]}
                                           {:file     "resources/public/js/select2.js"
                                            :file-min "resources/public/js/select2.min.js"
                                            :provides ["js-select2"]
                                            :requires ["cljsjs.jquery"]}
                                           {:file     "resources/public/js/jquery.jplayer.min.js"
                                            :requires ["cljsjs.jquery"]
                                            :provides ["js-jplayer"]}
                                           {:file     "resources/public/js/jplayer.js"
                                            :provides ["react-jplayer"]
                                            :requires ["js-jplayer" "cljsjs.react"
                                                       "cljsjs.jquery-ui"]}
                                           {:file     "resources/public/js/underscore-min.js"
                                            :provides ["underscore"]}
                                           {:file     "resources/public/js/griddle_0.2.15.js"
                                            :provides ["griddle"]
                                            :requires ["cljsjs.react" "underscore"]}]
                    :externs              ["resources/public/js/externs/react-bootstrap_0.28.1.ext.js"
                                           "resources/public/js/externs/select2.ext.js"
                                           "resources/public/js/externs/griddle_0.2.15.ext.js"]
                    :pretty-print         true}}}}

  :sassc [{:src       "src/scss/style.scss"
           :output-to "resources/public/css/style.css"}]
  :auto {"sassc" {:file-pattern #"\.(scss)$"}}

  :profiles {:dev     {:dependencies [[figwheel "0.5.0-SNAPSHOT"]
                                      [com.cemerick/piggieback "0.2.1"]
                                      [org.clojure/tools.nrepl "0.2.10"]
                                      [leiningen "2.5.1"]]

                       :repl-options {:init-ns          cglossa.server
                                      :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                       :plugins      [[lein-figwheel "0.5.0-SNAPSHOT"]]

                       :figwheel     {:css-dirs          ["resources/public/css"]
                                      :open-file-command "idea-opener"
                                      :server-port       3450}

                       :env          {:is-dev true}

                       :cljsbuild    {:builds
                                      {:app
                                       {:figwheel {:on-jsload "cglossa.core/main"}}}}}

             :uberjar {:hooks       [leiningen.cljsbuild]
                       :env         {:production true}
                       :omit-source true
                       :aot         :all
                       :cljsbuild   {:builds {:app
                                              {:compiler
                                               {:optimizations :advanced
                                                :pretty-print  false}}}}}})
