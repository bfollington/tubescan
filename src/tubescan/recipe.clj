(ns tubescan.recipe
  (:require [clojure.pprint :refer [pprint]]
            [tubescan.youtube :as yt]
            [tubescan.indexer :as idx]
            [tubescan.xtdb :as db]
            [xtdb.api :as xt]))

(def example-recipe
  {:from [[:channel {:username "lexfridman" :max-results 10}]]
   :where [[:my-rating :none]]})

(defn video->id [video]
  (get-in video ["snippet" "resourceId" "videoId"])) ;; presumptuous on what kind of resource this is

(defn list-channel [channel-id {:keys [max-results] :or {max-results 10}}]
  (->> (yt/channel-uploads channel-id {:max-results max-results})
       (map video->id)))

(defn filter-by-rating [rating results]
  (pprint "by rating")
  (pprint rating)
  (let [ratings (yt/my-rating-for-videos results)]
    (filter (fn [vid] (= (get ratings vid) (name rating))) results)))

(defn execute-where [[clause-type params] results]
  (if (some? clause-type)
    (case clause-type
      :my-rating (filter-by-rating params results)
      :else results)
    results))

(defn execute-from [[step-type params] results]
  (let [new-results (case step-type
                      :channel (let [{:keys [username max-results]} params]
                                 (list-channel username {:max-results max-results})))]

    (concat results new-results)))

(defn save-results! [session-id results]
  (let [doc {:xt/id (str "recording/" session-id)
             :results results}]
    (doseq [result results]
      (idx/index-video! result))
    (db/save-document! doc)))

(defn execute-substeps [steps handler-fn results]
  (print "STARTING NEW STEP")
  (pprint steps)
  (pprint results)
  (loop [results results
         remaining-steps steps]
    (let [next-step (first remaining-steps)]
      (if (some? next-step)
        (recur (handler-fn next-step results) (drop 1 remaining-steps))
        results))))

(defn execute-recipe! [session-id recipe]
  (let [{:keys [from where]} recipe
        dataset (execute-substeps from execute-from [])
        results (execute-substeps where execute-where dataset)]
    (print "ABOUT TO SAVE")
    (save-results! session-id results)
    results))

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