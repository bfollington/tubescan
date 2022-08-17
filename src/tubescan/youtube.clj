(ns tubescan.youtube
  (:require [clojure.java [io :as io]]
            [mount.core :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [tubescan.xtdb :as db]
            [xtdb.api :as xt])
  (:import (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)
           (java.io  InputStreamReader)
           (com.google.api.services.youtube YouTube$Builder)
           (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
           (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver)
           (com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets GoogleAuthorizationCodeFlow$Builder)
           (com.google.api.services.youtube.model Playlist PlaylistSnippet PlaylistItem PlaylistItemSnippet ResourceId)))

; This goddamn mess is based on the official Google example for Java
; see: https://developers.google.com/youtube/v3/quickstart/java
; and: https://developers.google.com/youtube/v3/quickstart/go (more up-to-date)

(def client-secrets-file-path "client_secret.json")
(def scopes ["https://www.googleapis.com/auth/youtube"])

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

(defn video-id->resource-id [video-id]
  (-> (ResourceId.)
      (.setKind "youtube#video")
      (.setVideoId video-id)))

;; API Read Methods

(defn channel-details [channel-id]
  (let [request (.. youtube (channels) (list "snippet,contentDetails,statistics"))]
    (-> request
        (.setId channel-id)
        (.execute))))


(defn video-details
  "Includes video statistics"
  [video-id]
  (let [request (.. youtube (videos) (list "snippet,statistics,contentDetails"))]
    (.setId request video-id)
    (-> (.execute request)
        (get "items")
        (first))))

(defn my-playlists []
  (let [request (.. youtube (playlists) (list "snippet,contentDetails"))]
    (.setMine request true)
    (-> (.execute request)
        (get "items"))))

(defn my-channels []
  (let [request (.. youtube (channels) (list "snippet,contentDetails"))]
    (.setMine request true)
    (-> (.execute request)
        (get "items"))))

(defn channel-uploads-playlist-id [channel-id]
  (let [request (.. youtube (channels) (list "contentDetails"))]
    (.setForUsername request channel-id)
    (-> (.execute request)
        (get "items")
        (first)
        (get "contentDetails")
        (get "relatedPlaylists")
        (get "uploads"))))

(defn channel-uploads [channel-id & {:keys [max-results] :or {max-results 10}}]
  (let [request (.. youtube (playlistItems) (list "snippet"))]
    (.setPlaylistId request (channel-uploads-playlist-id channel-id))
    (.setMaxResults request max-results)
    (-> (.execute request)
        (get "items"))))

(defn my-rating-for-video [video-id]
  (let [request (.. youtube (videos) (getRating "snippet"))]
    (.setId request video-id)
    (-> (get (.execute request) "items")
        (first)
        (get "rating"))))

(defn my-rating-for-videos [video-ids]
  (let [request (.. youtube (videos) (getRating "snippet"))]
    (.setId request (string/join "," video-ids))
    (->> (get (.execute request) "items")
         (map (fn [item] [(get item "videoId") (get item "rating")]))
         (into {}))))

(comment
  (my-rating-for-video "yw4N_GoIA-k"))

(defn my-likes []
  (let [request (.. youtube (playlistItems) (list "snippet"))]
    (-> request
        (.setPlaylistId "LL")
        (.setMaxResults 10)
        (.execute)
        (get "items"))))

(comment
  (my-likes)
  (my-playlists)
  (my-channels))

;; TODO: I broke pagination in here deliberately, be careful turning it back on
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

        ; hard cap maximum iterations
        (if (or (nil? page-token) (> counter 5))
          data
          (recur page-token (inc counter) data))))))

;; API Write Methods

(defn create-playlist! [name]
  (let [snippet (-> (PlaylistSnippet.)
                    (.setTitle name))
        playlist (-> (Playlist.)
                     (.setSnippet snippet))
        request (.. youtube (playlists) (insert "snippet" playlist))]
    (-> (.execute request)
        (get "id"))))


(defn add-video-to-playlist! [playlist-id video-id]
  (let [snippet (-> (PlaylistItemSnippet.)
                    (.setPlaylistId playlist-id)
                    (.setResourceId (video-id->resource-id video-id)))
        playlist-item (-> (PlaylistItem.)
                          (.setSnippet snippet))
        request (.. youtube (playlistItems) (insert "snippet" playlist-item))]
    (-> (.execute request))))
