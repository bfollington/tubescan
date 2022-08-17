(ns tubescan.models
  (:require [malli.core :as m]
            [malli.dev.pretty :refer [explain]]))

(def ChannelStep
  [:map
   [:name string?]
   [:after-date {:optional true} string?]
   [:order {:optional true} string?]
   [:max-results {:optional true} int?]])

(def ChannelsStep
  [:sequential string?])

(def Location
  [:tuple {:title "location"} :double :double :radius])

(def CaptionOptions
  [:enum :closed-caption])

(def SearchStep
  [:map
   [:query string?]
   [:relevance-language {:optional true} string?]
   [:captions {:optional true} CaptionOptions]
   [:location {:optional true} Location]
   [:published-after {:optional true} string?]
   [:order {:optional true} keyword?]
   [:max-results {:optional true} int?]])

(def EnhanceStep
  [:map
   [:id string?]
   [:depth {:optional true :min 1 :max 3} int?]
   [:max-results {:optional true} int?]])

(def RatingOptions [:enum :liked :disliked :none])

(def FromOption
  [:enum :search :channel :channels :playlist :enhanced-playlist])

(def FromTuple [:tuple FromOption any?])

(def WhereOption
  [:enum
   :minimum-views
   :minimum-likes
   :minimum-length
   :my-rating
   :excluding-channels])

(def WhereTuple [:tuple WhereOption any?])

(def Recipe
  [:map
   [:from [:sequential FromTuple]]
   [:where [:sequential WhereTuple]]])

(defn validate-recipe [recipe]
  (if (m/validate Recipe recipe)
    ()
    (explain Recipe recipe)))

(comment
  (validate-recipe {:from [[:enhanced-playlist {:playlist-id "id-xx" :depth 2 :max-results 3}]]

                    :where [[:my-rating nil]
                            [:minimum-length "10h"]
                            [:excluding-channels ["lexfridman"]]]}))
