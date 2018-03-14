(ns jira-worklog.post
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [environ.core :refer [env]]))

(def auth (env :shogren))
(def dry? (atom false))

(defn query [story]
  (client/get (str (env :url) "rest/api/2/issue/" story "/worklog")
             {:basic-auth auth
               :content-type :json
               :insecure? true
               :accept :json}))

(defn create-log [story user date time]
  (let [url (str (env :url) "rest/api/2/issue/" story "/worklog")
        body (json/write-str {:comment ""
                              :started date
                              :timeSpentSeconds time})]
    ;;(clojure.pprint/pprint {:user user :url url :body body})
    (if @dry?
      {:status 9999}
      (client/post url
                   {:basic-auth user
                    :body body
                    :query-params {:adjustEstimate "leave"}
                    :content-type :json
                    :insecure? true
                    :accept :json}))))

(defn get-active-sprints [board]
  (:values (json/read-json
            (:body (client/get (str (env :url) "rest/agile/1.0/board/" board "/sprint")
                               {:basic-auth auth
                                :content-type :json
                                :insecure? true
                                :query-params {"state" "active"}
                                :accept :json})))))




(defn get-issues-in-sprint [board sprint-id]
  (json/read-json
   (:body (client/get (str (env :url) "rest/agile/1.0/board/" board "/sprint/" sprint-id "/issue")
                      {:basic-auth auth
                       :content-type :json
                       :insecure? true
                       :accept :json}))))

(defn get-worklog [issue-id]
  (json/read-json
   (:body (client/get (str (env :url) "rest/api/2/issue/" issue-id "/worklog")
                      {:basic-auth auth
                       :content-type :json
                       :insecure? true
                       :accept :json}))))

(def logs  (let [sprintId (-> (get-active-sprints 146) first :id)
                   issues (get-issues-in-sprint 146 sprintId)
                   ids (map :id (:issues issues))
                   ]
               (mapcat :worklogs (map get-worklog ids))
               ))

(def logDates (sort (distinct (map (fn [log]
                                 (f/unparse (f/formatter-local "YYYY-MM-dd")
                                            (f/parse (:started log)))
                                 )
                               logs))))

