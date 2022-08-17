(ns tubescan.models)

(def ListChannelStep
  [:map
   [:name string?]
   [:after-date {:optional true} string?]
   [:order {:optional true} string?]
   [:max-results {:optional true} int?]])

(def Location
  [:map [:lat double?] [:long double?] [:radius {:optional true} int?]])

(def SearchStep
  [:map
   [:query string?]
   [:relevance-language {:optional true} string?]
   [:captions {:optional true} keyword?]
   [:location {:optional true} Location]
   [:published-after {:optional true} string?]
   [:order {:optional true} keyword?]
   [:max-results {:optional true} int?]])

(def EnhanceStep
  [:map
   [:id string?]
   [:depth {:optional true :min 1 :max 3} int?]
   [:max-results {:optional true} int?]])

(def WhereOption
  [:enum [:minimum-views
          :minimum-likes
          :minimum-length
          :not-rated-by-me
          :excluding-channels]])
