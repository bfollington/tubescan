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
  [:tuple {:title "location"} :double :double :int])

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
  [:enum
   :search
   :channel
   :channels
   :playlist
   :enhanced-playlist])

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

(def where-option->schema
  {:minimum-likes (m/schema :int {:min 0})
   :minimum-views (m/schema :int {:min 0})
   :minimum-length (m/schema :int {:min 0})
   :my-rating RatingOptions
   :excluding-channels [:sequential string?]})

(def from-option->schema
  {:search SearchStep
   :channel ChannelStep
   :channels ChannelsStep
   :playlist [:sequential string?]
   :enhanced-playlist EnhanceStep})

(defn validate-command
  [{:keys [label registry]}]

  (fn [[option value]]
    (let [schema (option registry)]
      (if (m/validate schema value)
        nil

        (let [error (m/explain schema value)]
          {:error-message (str "Invalid options for " label " " option error)
           :label label
           :option option
           :value value
           :error error})))))

(defn run-validations [validate xs]
  (->> xs
       (map validate)
       (filter some?)
       (into [])))

(defn validate-recipe [{:keys [from where] :as recipe}]
  (if (m/validate Recipe recipe)


    (let [inner-validations
          (apply concat [(run-validations (validate-command {:label "From" :registry from-option->schema}) from)
                         (run-validations (validate-command {:label "Where" :registry where-option->schema}) where)])]
      (if ((complement empty?) inner-validations)
				;; TODO: Enrich with providence
        {:errors inner-validations}
        true))


    ((explain Recipe recipe)
     {:errors (m/explain Recipe recipe)})))

(validate-recipe {:from [[:enhanced-playlist {:id "name" :depth 2 :max-results 3}]
                         [:search {:query "sdf" :location [10.0 10.0 10]}]]

                  :where [[:my-rating :none]
                          [:minimum-length 0]
                          [:excluding-channels ["lexfridman"]]]})
