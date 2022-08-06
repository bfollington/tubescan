(ns tubescan.app
  (:require [org.httpkit.server :as server]
            [nrepl.server :as nrepl]
            [tubescan.youtube :as youtube]
            [mount.core :refer [start defstate]])
  (:gen-class))

(def http-port (or (some-> (System/getenv "PORT")
                           parse-long)
                   8090))

(def nrepl-port (or (some-> (System/getenv "NREPL_PORT")
                            parse-long)
                    7777))

(defstate web-app
  :start (let [server (server/run-server
                       (fn [_req] {:body (str (youtube/search-videos {:related-to-video-id "yw4N_GoIA-k"}))})
                       {:port http-port})]
           (println "Site running on" (str "http://localhost:" http-port))
           server)
  :stop (web-app))

(defstate nrepl
  :start (let [server (nrepl/start-server :port nrepl-port)]
           (println "nREPL started on" (:port server)) server)
  :stop (nrepl/stop-server nrepl))

(defn -main [& _args] (start))