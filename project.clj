(defproject fentontravers/websocket-server "0.4.12"
  :description "WebSocket Server Library"
  :url "https://github.com/ftravers/websocket-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [http-kit "2.2.0"]
                 [com.taoensso/timbre "4.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/data.json "0.2.6"]]
  :target-path "target/%s"

  :profiles {:dev {:source-paths ["dev" "src"]
                   :dependencies [[stylefruits/gniazdo "1.0.1"]
                                  [org.clojure/core.async "0.4.474"]]}
             :uberjar {:aot :all}})
