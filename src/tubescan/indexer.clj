(ns tubescan.indexer
  (:require [tubescan.youtube :as yt]
            [tubescan.xtdb :as db]
            [xtdb.api :as xt]))


(comment
  (yt/search-videos {:related-to-video-id "yw4N_GoIA-k"}))

; find all videos related to a video by ID
(comment
  (xt/q (xt/db db/xtdb-node) '{:find [e]
                               :where [[e :video/related-to-video-id "yw4N_GoIA-k"]]}))
; get all fields of related videos by ID
(comment
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [(pull ?video [*])]
     :where [[?video :video/related-to-video-id "yw4N_GoIA-k"]]}))

; create a DB function to update video statistics
(comment
  (xt/submit-tx
   db/xtdb-node
   [[::xt/put
     {:xt/id :update-statistics
                      ;; note that the function body is quoted.
                      ;; and function calls are fully qualified
      :xt/fn '(fn [ctx eid statistics]
                (let [db (xtdb.api/db ctx)
                      entity (xtdb.api/entity db eid)]
                  [[::xt/put (assoc entity :video/statistics statistics)]]))}]]))

(defn video->statistics [video]
  {:views (get-in video ["statistics" "viewCount"])
   :comments (get-in video ["statistics" "commentCount"])
   :favorites (get-in video ["statistics" "favoriteCount"])
   :likes (get-in video ["statistics" "likeCount"])})

; submit a transaction to update a video's statistics by id
(defn update-video-statistics! [video-id statistics]
  (xt/submit-tx
   db/xtdb-node
   [[::xt/fn :update-statistics video-id statistics]]))

; get a video's stats and decorate its existing record with the info
(comment
  (->> (yt/video-details "crt1MRMBf3I")
       (video->statistics)
       (update-video-statistics! "crt1MRMBf3I")))

; view that video's full record
(comment
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [(pull ?video [*])]
     :where [[?video :video/id "crt1MRMBf3I"]]}))


(defn video->db [details {:keys [rating related-to-video-id]}]
  {:xt/id (get-in details ["id"])
   :video/id (get-in details ["id"])
   :video/rating rating
   :video/related-to-video-id related-to-video-id
   :video/channel-id (get-in details ["snippet" "channelId"])
   :video/title (get-in details ["snippet" "title"])
   :video/description (get-in details ["snippet" "description"])
   :video/statistics {:views (get-in details ["statistics" "viewCount"])
                      :comments (get-in details ["statistics" "commentCount"])
                      :favorites (get-in details ["statistics" "favoriteCount"])
                      :likes (get-in details ["statistics" "likeCount"])}})

; get the video details, statistics, liked status etc.
(defn index-video! [video-id]
  (let [details (yt/video-details video-id)
        rating (yt/my-rating-for-video video-id)
        doc (video->db details rating)]
    (db/save-document! doc)))

(comment
  (index-video! "yw4N_GoIA-k"))

(comment
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [(pull ?video [*])]
     :where [[?video :video/id "yw4N_GoIA-k"]]}))