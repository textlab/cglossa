(defproject cglossa "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [com.cognitect/transit-clj "0.8.295"]
                 [compojure "1.5.1"]
                 [enlive "1.1.6"]
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
                 [buddy/buddy-hashers "1.0.0"]
                 [adzerk/env "0.3.1"]
                 [binaryage/devtools "0.8.3"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-sassc "0.10.4"]
            [lein-auto "0.1.3"]
            [lein-exec "0.3.6"]]

  :min-lein-version "2.5.0"

  :uberjar-name "cglossa.jar"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :main cglossa.server

  :sassc [{:src       "src/scss/style.scss"
           :output-to "resources/public/css/style.css"}]
  :auto {"sassc" {:file-pattern #"\.(scss)$"}}

  :profiles {:dev     {:dependencies [[leiningen "2.7.1"]
                                      [ring/ring-mock "0.3.0"]]

                       :repl-options {:init-ns cglossa.server
                                      :port    8230}

                       :env          {:is-dev "1"}}

             :uberjar {:env         {:production true}
                       :omit-source true
                       :aot         :all}})
