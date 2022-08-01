(ns acme.youtube
  (:require [clojure.java [io :as io]]
            [mount.core :refer [defstate]])
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
  (let [flow (-> (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory secrets (java.util.ArrayList. scopes))
                 (.build))
        credential (AuthorizationCodeInstalledApp. flow (LocalServerReceiver.))]
    (.authorize credential "user")))

(defn build-youtube [credential]
  (println credential)
  (-> (YouTube$Builder. http-transport json-factory credential)
      (.setApplicationName application-name)
      (.build)))

(defstate google-credentials :start (build-credential))
(defstate youtube :start (build-youtube google-credentials))

(defn channel-details [channel-id]
  (let [request (.list (.channels youtube) "snippet,contentDetails,statistics")]
    (-> request
        (.setId channel-id)
        (.execute))))
