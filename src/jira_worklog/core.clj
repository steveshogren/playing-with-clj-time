(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            ))

(def auth auth)

(defn query []
  (client/get "https://jira.smartstream-stp.com/rest/api/2/issue/CORE-7571/worklog"
              {:basic-auth auth
               :content-type :json
               :insecure? true
               :accept :json}))

(defn update []
  (client/put "https://jira.smartstream-stp.com/rest/api/2/issue/201843/worklog/162169"
              {:basic-auth auth
               :body (json/write-str {:comment "I did some work here."
                                      :author {:name "boe"}
                                      :timeSpentSeconds (* 60 60 1)})
               :content-type :json
               :insecure? true
               :accept :json}))

(defn create []
  (client/post "https://jira.smartstream-stp.com/rest/api/2/issue/CORE-7571/worklog"
               {:basic-auth auth
                :body (json/write-str {:comment "I did some work here."
                                       :author {:name "boe"}
                                       :timeSpentSeconds (* 60 60 1)})
                :content-type :json
                :insecure? true
                :accept :json}))


;; (:self (first (:worklogs (json/read-json (:body (query))))))

(defn get-stories []
  (let [sid (:id (first (:values (json/read-json
                                  (:body (client/get "https://jira.smartstream-stp.com/rest/agile/1.0/board/146/sprint"
                                                     {:basic-auth auth
                                                      :content-type :json
                                                      :insecure? true
                                                      :query-params {"state" "active"}
                                                      :accept :json}
                                                     ))))))
        issueGet (str "https://jira.smartstream-stp.com/rest/agile/1.0/board/146/sprint/" sid "/issue")
        issues (:issues (json/read-json
                         (:body (client/get issueGet
                                            {:basic-auth auth
                                             :content-type :json
                                             :insecure? true
                                             :accept :json}
                                            ))))]
    (map (fn [a]
           {:desc (:summary (:fields a))
            :id (:id a)})
         issues)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
