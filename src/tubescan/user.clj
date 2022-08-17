(ns user
  (:require [mount.core :refer [start stop-except]]
            [tubescan.xtdb :refer [xtdb-node]] ;; Unused, but we need the imports for mount deps ordering.
            [tubescan.youtube :refer [google-credentials youtube]])) ;; Same here.

(let [launched (atom false)]

  (defn start-app []
    (println "Starting tubescan. This may take a moment. ")
    (when (not @launched) (println "You will need to go through the OAUTH flow in the browser."))
    (swap! launched (fn [_] true))
    (start)))

(defn stop-app []
  (stop-except #'google-credentials))

(defn restart-app []
  (stop-app)
  (start-app))
