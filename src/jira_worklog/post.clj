(ns jira-worklog.post
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clojure.data.codec.base64 :as base64]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            )
  )

(def auth (env :shogren))

(defn query [user story]
  (client/get (str (env :url) "rest/api/2/issue/" story "/worklog")
              {:basic-auth user
               :content-type :json
               :insecure? true
               :accept :json}))

(defn create-log [story user date time]
  (client/post (str (env :url) "rest/api/2/issue/" story "/worklog")
               {:basic-auth user
                :body (json/write-str {:comment ""
                                       :started date
                                       :timeSpentSeconds time})
                :query-params {:adjustEstimate "leave"}
                :content-type :json
                :insecure? true
                :accept :json}))

(defn get-active-sprints [board]
  (:values (json/read-json
            (:body (client/get (str (env :url) "rest/agile/1.0/board/" board "/sprint")
                               {:basic-auth auth
                                :content-type :json
                                :insecure? true
                                :query-params {"state" "active"}
                                :accept :json})))))

(defn get-issues-in-sprint [board sid]
  (json/read-json
   (:body (client/get (str (env :url) "rest/agile/1.0/board/" board "/sprint/" sid "/issue")
                      {:basic-auth auth
                       :content-type :json
                       :insecure? true
                       :accept :json}))))
