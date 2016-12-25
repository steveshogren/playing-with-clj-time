(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [jira-worklog.post :as p]))

(defn myformat [d]
  (f/unparse (f/formatter-local "YYYY-MM-dd'T'hh:mm:ss.sssZ")
             (l/to-local-date-time d)))

(defn today-8am []
  (myformat
   (if-let [d (f/parse (:date-override (get-data)))]
     (t/plus d (t/hours 7))
     (t/today-at 7 0 0))))

(defn today-1pm []
  (myformat
   (if-let [d (f/parse (:date-override (get-data)))]
     (t/plus d (t/hours 11) (t/minutes 59))
     (t/today-at 11 59 0))))

(defn- byte-transform [direction-fn string]
  (try
    (apply str (map char (direction-fn (.getBytes string))))
    (catch Exception _)))

(def four-hours (* 60 60 4))
(def eight-hours (* 60 60 8))

(defn create
  ([user date story] (create user date story four-hours))
  ([user date story time]
   (try
     (:status (p/create-log story user date time))
     (catch Exception e
       (do (println "failed: " user)
           500)))))

(defn get-stories [board]
  (let [sprint-id (-> (p/get-active-sprints board) first :id)
        issues (:issues (p/get-issues-in-sprint board sprint-id))
        all-from-sprint (map (fn [a]
                               {:desc (-> a :fields :summary)
                                :statusName (-> a :fields :status :name)
                                :status (-> a :fields :status :statusCategory :key)
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

(defn get-data []
  (read-string (slurp "data.clj")))

(defn foo [& args]
  (let [peeps (get-data)
        v5 [(:core peeps) 146]
        holiday (:holiday peeps)
        web [(:web peeps) 0]
        reporting [(:reporting peeps) 147]]
    (concat (log-holiday holiday "CORE-7951")
            (log-day v5)
            (log-day reporting))))
