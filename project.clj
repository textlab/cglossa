(defproject cglossa "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [com.cognitect/transit-clj "0.8.295"]
                 [compojure "1.5.1"]
                 [enlive "1.1.6"]
                 [reagent "0.6.0"]
                 [cljsjs/jquery "2.2.4-0"]
                 [cljsjs/react-bootstrap "0.30.6-0"]
                 [environ "1.1.0"]
                 [http-kit "2.2.0"]
                 [cljs-http "0.1.42"]
                 [prone "1.1.2"]
                 [korma "0.4.3"]
                 #_[mysql/mysql-connector-java "6.0.5"] ; need older version on our server
                 [mysql/mysql-connector-java "5.1.37"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/data.avl "0.0.17"]
                 [me.raynes/conch "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [cheshire "5.6.3"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [ring-logger-timbre "0.7.5"]
                 [dk.ative/docjure "1.11.0"]
                 [adzerk/env "0.3.1"]
                 [binaryage/devtools "0.8.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-environ "1.1.0"]
            [lein-sassc "0.10.4"]
            [lein-auto "0.1.3"]
            [lein-exec "0.3.6"]]

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
                    :optimizations        :none
                    :cache-analysis       true
                    :recompile-dependents false
                    :main                 "cglossa.core"
                    :asset-path           "js/out"
                    :foreign-libs         [{:file     "resources/public/js/jquery-ui.js"
                                            :file-min "resources/public/js/jquery-ui.min.js"
                                            :provides ["jquery-ui"]
                                            :requires ["cljsjs.jquery"]}
                                           {:file     "resources/public/js/select2.js"
                                            :file-min "resources/public/js/select2.min.js"
                                            :provides ["js-select2"]
                                            :requires ["cljsjs.jquery"]}
                                           {:file     "resources/public/js/jquery.jplayer.js"
                                            :file-min "resources/public/js/jquery.jplayer.min.js"
                                            :requires ["cljsjs.jquery"]
                                            :provides ["js-jplayer"]}
                                           {:file     "resources/public/js/jplayer.js"
                                            :file-min "resources/public/js/jplayer.min.js"
                                            :provides ["react-jplayer"]
                                            :requires ["js-jplayer" "cljsjs.react" "jquery-ui"]}
                                           {:file     "resources/public/js/wfplayer.js"
                                            :provides ["react-wfplayer"]
                                            :requires ["cljsjs.react"]}
                                           {:file     "resources/public/js/jquery.colorpicker.js"
                                            :file-min "resources/public/js/jquery.colorpicker.min.js"
                                            :requires ["cljsjs.jquery"]
                                            :provides ["colorpicker"]}
                                           {:file     "resources/public/js/react-spinner.js"
                                            :file-min "resources/public/js/react-spinner.min.js"
                                            :provides ["react-spinner"]}
                                           {:file     "resources/public/js/underscore-min.js"
                                            :provides ["underscore"]}
                                           {:file     "resources/public/js/bootstrap_tooltip.js"
                                            :file-min "resources/public/js/bootstrap_tooltip.min.js"
                                            :provides ["tooltip"]
                                            :requires ["cljsjs.jquery"]}
                                           {:file     "resources/public/js/griddle_0.6.0.js"
                                            :file-min "resources/public/js/griddle_0.6.0.min.js"
                                            :provides ["griddle"]
                                            :requires ["cljsjs.react" "underscore"]}
                                           {:file     "resources/public/js/geo_distribution_map.js"
                                            :provides ["geo-distribution-map"]
                                            :requires ["cljsjs.react"]}]
                    :externs              ["resources/public/js/externs/extra.ext.js"
                                           "resources/public/js/externs/select2.ext.js"
                                           "resources/public/js/externs/react-spinner.ext.js"
                                           "resources/public/js/externs/bootstrap_tooltip.ext.js"
                                           "resources/public/js/externs/griddle_0.6.0.ext.js"
                                           "resources/public/js/externs/jplayer.ext.js"
                                           "resources/public/js/externs/wfplayer.ext.js"
                                           "resources/public/js/externs/geo_distribution_map.ext.js"]
                    :pretty-print         true}}}}

  :sassc [{:src       "src/scss/style.scss"
           :output-to "resources/public/css/style.css"}]
  :auto {"sassc" {:file-pattern #"\.(scss)$"}}

  :profiles {:dev     {:dependencies [[leiningen "2.7.1"]
                                      [ring/ring-mock "0.3.0"]]

                       :repl-options {:init-ns cglossa.server
                                      :port    8230}

                       :plugins      [[lein-figwheel "0.5.8"]]

                       :figwheel     {:css-dirs          ["resources/public/css"]
                                      :open-file-command "idea-opener"
                                      :server-port       3450}

                       :env          {:is-dev "1"}

                       :cljsbuild    {:builds
                                      {:app
                                       {:figwheel {:on-jsload "cglossa.core/main"}
                                        :compiler {:source-map           true
                                                   :source-map-timestamp true}}}}}

             :uberjar {:hooks       [leiningen.cljsbuild]
                       :env         {:production true}
                       :omit-source true
                       :aot         :all
                       :cljsbuild   {:builds {:app
                                              {:compiler
                                               {:optimizations :advanced
                                                :source-map    "resources/public/js/out/out.js.map"
                                                :pretty-print  false}}}}}})
