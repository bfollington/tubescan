(ns tubescan.xtdb
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [xtdb.api :as xt]
            [clojure.edn :as edn])
  (:import (java.time Duration)))

(defn ->txn [video] [::xt/put video])
(defn unpack [result] (first (first result)))

(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))

(def db-spec (load-edn "db.edn"))

(defn start-xtdb-pg! []
  (xt/start-node
   {:xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                  :connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                    :pool-opts {}
                                    :db-spec db-spec}
                  :poll-sleep-duration (Duration/ofSeconds 1)}
    :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                          :connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                            :pool-opts {}
                                            :db-spec db-spec}}}))

(defn start-xtdb-sqlite! []
  (xt/start-node
   {:xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                  :connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.sqlite/->dialect}
                                    :pool-opts {}
                                    :db-spec db-spec}
                  :poll-sleep-duration (Duration/ofSeconds 1)}
    :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                          :connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.sqlite/->dialect}
                                            :pool-opts {}
                                            :db-spec db-spec}}}))


(defstate xtdb-node
  :start (start-xtdb-sqlite!)
  :stop (.close xtdb-node))

(defn save-document! [doc]
  (print "saving")
  (print doc)
  (xt/submit-tx xtdb-node [(->txn doc)]))

(comment
  (xt/submit-tx xtdb-node [[::xt/put
                            {:xt/id "hi2u"
                             :user/name "zag"}]]))

(comment
  (xt/q (xt/db xtdb-node) '{:find [e]
                            :where [[e :user/name "zag"]]}))
