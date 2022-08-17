(ns tubescan.recipe
  (:require [clojure.pprint :refer [pprint]]
            [tubescan.youtube :as yt]
            [tubescan.indexer :as idx]
            [tubescan.xtdb :as db]
            [xtdb.api :as xt]))

(def example-recipe
  {:steps [[:list-channel "lexfridman" {:max-results 10}]
           [:where [:not-rated-by-me]]]})

(defn video->id [video]
  (get-in video ["snippet" "resourceId" "videoId"])) ;; presumptuous on what kind of resource this is

(defn list-channel [channel-id {:keys [max-results] :or {max-results 10}}]
  (->> (yt/channel-uploads channel-id {:max-results max-results})
       (map video->id)))

(defn filter-not-rated [results]
  (let [ratings (yt/my-rating-for-videos results)]
    (filter (fn [vid] (= (get ratings vid) "none")) results)))

(defn where [results clauses]
  (loop [remaining-clauses clauses
         filtered-results results]
    (let [[clause-type _] (first remaining-clauses)
          remaining-clauses (drop 1 remaining-clauses)]
      (if (some? clause-type)
        (case clause-type
          :not-rated-by-me (recur remaining-clauses (filter-not-rated results))
          :else (recur remaining-clauses filtered-results))
        filtered-results))))

(defn execute-step [[step-type & args] results]
  (let [results' (case step-type
                   :list-channel (let [[username params] args]
                                   (list-channel username params))
                   :where (where results args))]

    results'))

(defn save-results! [session-id results]
  (let [doc {:xt/id (str "recording/" session-id)
             :results results}]
    (doseq [result results]
      (idx/index-video! result))
    (db/save-document! doc)))

(defn execute-recipe! [session-id recipe]
  (let [{:keys [steps]} recipe]
    (loop [results []
           remaining-steps steps]
      (if (> (count remaining-steps) 0)
        (let [next-step (first remaining-steps)]
          (recur (execute-step next-step results) (drop 1 remaining-steps)))
        (do (save-results! session-id results)
            results)))))

(comment
  "Run the example recipe and persist the results"
  (execute-recipe! "test-session" example-recipe))

(defn session-results [session-id]
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [?video]
     :where [[?session :xt/id session-id]
             [?session :results ?video]]
     :in [session-id]} (str "recording/" session-id)))

(comment (session-results "test-session"))

(comment
  "Fetch full session result set"
  (let [vids (session-results "test-session")
        results (xt/pull-many (xt/db db/xtdb-node) [:video/id :video/title :video/statistics] (first (first vids)))]
    results))

(comment
  "Save all session-results to a playlist"
  (let [vids (db/unpack (session-results "test-session"))]
    (doseq [vid vids]
      (yt/add-video-to-playlist! "PLKbIl8vl5RyuBC9e3dHvSCrKuRSCPWp67" vid))))

(comment
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [(pull ?video [*])]
     :where [[?video :video/id "Q6tDV3BhrcM"]]}))