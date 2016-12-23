(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clojure.data.codec.base64 :as base64]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            ))

(defn myformat [d]
  (f/unparse (f/formatter-local "YYYY-MM-dd'T'hh:mm:ss.sssZ")
             (l/to-local-date-time d)))

(defn today-8am []
  (myformat (t/today-at 7 0 0)))

(defn today-1pm []
  (myformat  (t/today-at 11 (-  59 (rand-int 0)) 0)))

(defn- byte-transform [direction-fn string]
  (try
    (apply str (map char (direction-fn (.getBytes string))))
    (catch Exception _)))

(defn- encode-base64 [^String string]
  (byte-transform base64/encode string))

(def auth (env :shogren))

(defn query [user story]
  (client/get (str (env :url) "rest/api/2/issue/" story "/worklog")
              {:basic-auth user
               :content-type :json
               :insecure? true
               :accept :json}))

(def four-hours (* 60 60 4))
(def eight-hours (* 60 60 8))

(defn create
  ([user date story] (create user date story four-hours))
  ([user date story time]
   (try
     (:status (client/post (str (env :url) "rest/api/2/issue/" story "/worklog")
                           {:basic-auth user
                            :body (json/write-str {:comment ""
                                                   :started date
                                                   :timeSpentSeconds time})
                            :query-params {
                                           :adjustEstimate "leave"
                                           }
                            :content-type :json
                            :insecure? true
                            :accept :json}))
     (catch Exception e
       (do (println "failed: " user)
           500)))))

(defn get-stories [board]
  (let [sid (:id (first (:values (json/read-json
                                  (:body (client/get (str (env :url) "rest/agile/1.0/board/" board "/sprint")
                                                     {:basic-auth auth
                                                      :content-type :json
                                                      :insecure? true
                                                      :query-params {"state" "active"}
                                                      :accept :json}
                                                     ))))))
        issueGet (str (env :url) "rest/agile/1.0/board/" board "/sprint/" sid "/issue")
        issues (:issues (json/read-json
                         (:body (client/get issueGet
                                            {:basic-auth auth
                                             :content-type :json
                                             :insecure? true
                                             :accept :json}
                                            ))))
        all-from-sprint (map (fn [a]
                               {:desc (:summary (:fields a))
                                :statusName (:name (:status (:fields a)))
                                :status (:key (:statusCategory  (:status (:fields a))))
                                :id (:id a)})
                             issues)]
    (filter #(not (or (= "new" (:status %))
                      ;; (= "Awaiting Acceptance" (:statusName %))
                      (= "done" (:status %)))) all-from-sprint)))

(defn get-story-peep-pairs [peeps board]
  (let [stories (get-stories board)]
    (map (fn [peep]
           [(:id (rand-nth stories)) peep]) peeps)))

(defn log-time [[peeps board] time-f]
  (reduce (fn [r [story peep]]
            (let [result  [peep (create (env peep) (time-f) story)]]
              (println "success: " result)
              (conj r result)))
          []
          (get-story-peep-pairs peeps board)))

(defn log-day [peeps-n-board]
  (log-time peeps-n-board today-8am)
  (log-time peeps-n-board today-1pm))

(defn log-holiday [peeps story]
  (reduce (fn [r peep]
            (let [result [peep (create (env peep) (today-8am) story eight-hours)]]
              (println "success: " result)
              (conj r result)))
          []
          peeps))

(defn foo [& args]
  (let [peeps (read-string (slurp "data.clj"))
        v5 [(:core peeps) 146]
        holiday (:holiday peeps)
        web [(:web peeps) 0]
        reporting [(:reporting peeps) 147]]
    (concat (log-holiday holiday "CORE-7951")
            (log-day v5)
            (log-day reporting))))
