(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clojure.data.codec.base64 :as base64]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            ))

(defn format [d]
  (f/unparse (f/formatter "YYYY-MM-dd'T'hh:mm:ss.sssZ") (l/to-local-date-time d)))

(defn today-8am []
  (format (t/today-at 8 30 0)))

(defn today-1am []
  (format  (t/today-at 12 30 0)))

(defn- byte-transform [direction-fn string]
  (try
    (apply str (map char (direction-fn (.getBytes string))))
    (catch Exception _)))

(defn- encode-base64 [^String string]
  (byte-transform base64/encode string))

(def auth (env :shogren))

(defn query []
  (client/get (str (env :url) "rest/api/2/issue/CORE-7571/worklog")
              {:basic-auth auth
               :content-type :json
               :insecure? true
               :accept :json}))

(defn create [user date]
  (client/post (str (env :url) "rest/api/2/issue/CORE-7571/worklog")
               {:basic-auth user
                :body (json/write-str {:comment ""
                                       :started date
                                       :timeSpentSeconds (+
                                                          ;;(* (- (rand-int 3) 1) (* 60 (rand-int 15)))
                                                          (* 60 60 4))})
                :query-params {
                               :adjustEstimate "leave"
                               }
                :content-type :json
                :insecure? true
                :accept :json}))

(create auth (today-8am))

(today-8am)


;; (:self (first (:worklogs (json/read-json (:body (query))))))

(defn get-stories []
  (let [sid (:id (first (:values (json/read-json
                                  (:body (client/get (str (env :url) "rest/agile/1.0/board/146/sprint")
                                                     {:basic-auth auth
                                                      :content-type :json
                                                      :insecure? true
                                                      :query-params {"state" "active"}
                                                      :accept :json}
                                                     ))))))
        issueGet (str (env :url) "rest/agile/1.0/board/146/sprint/" sid "/issue")
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

;;(get-stories)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
