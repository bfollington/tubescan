(ns tubescan.youtube
  (:require [clojure.java [io :as io]]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]
            [tubescan.xtdb :as db]
            [xtdb.api :as xt])
  (:import (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)
           (java.io  InputStreamReader)
           (com.google.api.services.youtube YouTube$Builder)
           (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
           (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver)
           (com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets GoogleAuthorizationCodeFlow$Builder)))

; This goddamn mess is based on the official Google example for Java
; see: https://developers.google.com/youtube/v3/quickstart/java
; and: https://developers.google.com/youtube/v3/quickstart/go (more up-to-date)

(def client-secrets-file-path "client_secret.json")
(def scopes ["https://www.googleapis.com/auth/youtube.readonly"])

(def application-name "My App")
(def json-factory (GsonFactory/getDefaultInstance))
(def secrets (GoogleClientSecrets/load json-factory (InputStreamReader. (io/input-stream client-secrets-file-path))))
(def http-transport (GoogleNetHttpTransport/newTrustedTransport))

(defn build-credential []
  (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory secrets scopes)
      (.build)
      (AuthorizationCodeInstalledApp. (LocalServerReceiver.))
      (.authorize "user")))

(defn build-youtube [credential]
  (-> (YouTube$Builder. http-transport json-factory credential)
      (.setApplicationName application-name)
      (.build)))

(defstate google-credentials :start (build-credential))
(defstate youtube :start (build-youtube google-credentials))

(defn channel-details [channel-id]
  (let [request (.. youtube (channels) (list "snippet,contentDetails,statistics"))]
    (-> request
        (.setId channel-id)
        (.execute))))

(defn video-details [video-id]
  (let [request (.. youtube (videos) (list "snippet,statistics,contentDetails"))]
    (.setId request video-id)
    (-> (.execute request)
        (get "items")
        (first))))

(defn video->statistics [video]
  (pprint video)
  {:views (get-in video ["statistics" "viewCount"])
   :comments (get-in video ["statistics" "commentCount"])
   :favorites (get-in video ["statistics" "favoriteCount"])
   :likes (get-in video ["statistics" "likeCount"])})

(defn search-videos [{:keys [related-to-video-id channel-id order max-results
                             published-after published-before query
                             relevance-language video-category-id video-duration]}]
  (loop [page-token nil
         counter 0
         data []]
    (let [req (.. youtube (search) (list "snippet"))]

      ;; (when (some? page-token)
      ;;   (.setPageToken req page-token))

      (when related-to-video-id
        (.setRelatedToVideoId req related-to-video-id))
      (when channel-id
        (.setChannelId req channel-id))
      (when order
        (.setOrder req order))
      (when published-after
        (.setPublishedAfter req published-after))
      (when published-before
        (.setPublishedBefore req published-before))
      (when query
        (.setQ req query))
      (when relevance-language
        (.setRelevanceLanguage req relevance-language))
      (when video-category-id
        (.setVideoCategoryId req video-category-id))
      (when video-duration
        (.setVideoDuration req video-duration))

      (.setMaxResults req 10)
      (when max-results
        (.setMaxResults req max-results))
      (.setType req "video")

      (let [res (.execute req)
            page-token (get res "nextPageToken")
            data (conj data res)]

        (if (or (nil? page-token) (> counter 5))
          data
          (recur page-token (inc counter) data))))))

(defn pick-video-fields [related-to-video-id video]

  {:xt/id (get-in video ["id" "videoId"])
   :video/id (get-in video ["id" "videoId"])
   :video/related-to-video-id related-to-video-id
   :video/channel-id (get-in video ["snippet" "channelId"])
   :video/title (get-in video ["snippet" "title"])
   :video/description (get-in video ["snippet" "description"])})

(defn video->txn [video] [::xt/put video])

; get related videos, persist basic summary into db
(comment
  (let [data (search-videos {:related-to-video-id "yw4N_GoIA-k"})
        items (get (first data) "items")
        txns (map (comp video->txn (partial pick-video-fields "yw4N_GoIA-k")) items)]
    (pprint data)
    (xt/submit-tx db/xtdb-node (vec txns))))

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

; submit a transaction to update a video's statistics by id
(defn update-video-statistics! [video-id statistics]
  (xt/submit-tx
   db/xtdb-node
   [[::xt/fn :update-statistics video-id statistics]]))

; get a video's stats and decorate its existing record with the info
(comment
  (->> (video-details "crt1MRMBf3I")
       (video->statistics)
       (update-video-statistics! "crt1MRMBf3I")))

; view that video's full record
(comment
  (xt/q
   (xt/db db/xtdb-node)
   '{:find [(pull ?video [*])]
     :where [[?video :video/id "crt1MRMBf3I"]]}))