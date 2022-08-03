(ns youtube-explorer.youtube
  (:require [clojure.java [io :as io]]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [youtube-explorer.xtdb :as db]
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

(defn search-videos [{:keys [related-to-video-id channel-id order max-results
                             published-after published-before query
                             relevance-language video-category-id video-duration]}]
  (loop [page-token nil
         data []]
    (let [req (.. youtube (search) (list "snippet"))]

      (when (some? page-token)
        (.setPageToken req page-token))

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

      (.setMaxResults req 50)
      (when max-results
        (.setMaxResults req max-results))
      (.setType req "video")

      (let [res (.execute req)
            page-token (get res "nextPageToken")
            data (conj data res)]

        (if (nil? page-token)
          data
          (recur page-token data))))))

(defn pick-video-fields [related-to-video-id video]
  {:xt/id (get-in video ["id" "videoId"])
   :id (get-in video ["id" "videoId"])
   :related-to-video-id related-to-video-id
   :channel-id (get-in video ["snippet" "channelId"])
   :title (get-in video ["snippet" "title"])
   :description (get-in video ["snippet" "description"])})

(defn video->txn [video] [::xt/put video])

(comment
  (let [data (search-videos {:related-to-video-id "yw4N_GoIA-k"})
        items (get (first data) "items")
        txns (map (comp (partial pick-video-fields "yw4N_GoIA-k") video->txn) items)]
    (pprint txns)
    (xt/submit-tx db/xtdb-node txns)))

(comment
  (xt/q (xt/db db/xtdb-node) '{:find [e]
                               :where [[e :related-to-video-id "yw4N_GoIA-k"]]}))