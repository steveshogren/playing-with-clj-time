(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [clj-time.predicates :as pred]
            [jira-worklog.post :as p]
            [jira-worklog.creds :as creds]
            ))

(def run-data (read-string (slurp "data.clj")))

(defn myformat [d]
  (f/unparse (f/formatter-local "YYYY-MM-dd'T'hh:mm:ss.sssZ")
             (l/to-local-date-time d)))

(defn today-8am-date [date-override]
  (if-let [d (f/parse date-override)]
    (t/plus d (t/hours 7))
    (t/today-at 7 0 0)))

(defn today-8am [date-override]
   (myformat (today-8am-date date-override)))

(def eight-hours (* 60 60 8))

(defn create
  ([user date story] (create user date story eight-hours))
  ([user date story time]
   (try
     (:status (p/create-log story user date time))
     (catch Exception e
       (do (println "failed: " user)
           500)))))

(comment (choose-sprint [{:id 11 :name "test"} {:id 22 :name "other"}]))

(defn choose-sprint [sprints]
  (if (= 1 (count sprints))
    (-> sprints first :id)
    (do
      (println "Multiple sprints found:")
      (doseq [sprint sprints]
        (println sprint))
      (println "Enter a zero-based index to select:")
      (:id (nth sprints (Integer/parseInt (read-line)))))))


(defn get-stories [board]
  (let [sprints (p/get-active-sprints board)
        sprint-id (choose-sprint sprints)
        issues (:issues (p/get-issues-in-sprint board sprint-id))
        all-from-sprint (map (fn [a]
                               {:desc (-> a :fields :summary)
                                :statusName (-> a :fields :status :name)
                                :status (-> a :fields :status :statusCategory :key)
                                :id (:id a)})
                             issues)]
    (filter #(not (or (= "new" (:status %))
                      (contains? (:filterStatus run-data) (:statusName %))
                      (= "done" (:status %)))) all-from-sprint)))

(comment
  (get-logs 146)
  )
(defn get-logs [board]
  (let [stories (get-stories board)
        logs (mapcat (comp p/query :id) stories)]
    logs))

(defn get-story-peep-pairs [[peeps board] time]
  (let [stories (get-stories board)]
    (map (fn [peep]
           (let [story (rand-nth stories)]
             [(:id story) peep (:desc story) time]))
         peeps)))

(defn confirm-logs? [stories-and-peeps]
  (doseq [[story peep desc time] stories-and-peeps]
    (println peep " - " time " - " desc ))
  (println "Submit logs? y/n")
  (= "y" (read-line)))

(defn log-all-time [stories-and-peeps-and-time-f]
  (if (and (< 0 (count stories-and-peeps-and-time-f))
           (confirm-logs? stories-and-peeps-and-time-f)
           )
    (reduce (fn [r [story peep desc time]]
              (let [result [peep (create (creds/get-creds peep) time story)]]
                (conj r result)))
            []
            stories-and-peeps-and-time-f)
    (println "Logging Aborted! No logs posted.")))

(defn collect-rand-story-from-board [peeps-n-board date-override]
  (get-story-peep-pairs peeps-n-board (today-8am date-override)))

(defn collect-single-day [peeps story date-override]
  (map (fn [peep] [story peep story (today-8am date-override)]) peeps))

(defn last-two-weeks []
  (let [today (t/today)]
    (->> (range 20)
        (map #(t/minus today (t/days %)) )
        (filter (comp not pred/weekend?))
        (map str)
        )))

(defn printhistory []
  (println "missing days incoming!")
  (let [logged (set (p/log-dates))
        weekdays (set (last-two-weeks))
        missing (set/difference weekdays logged) 
        dates (map (fn [x] (str "\"" x "\"" ))
                   (reverse (sort missing)))]
    (println (str "[ " dates " ]"))))
;; (printhistory)

(defn collect-all-users [peeps date]
  (let [core-holiday-col (collect-single-day (:core-holiday peeps) (:core-holiday-issue peeps) date)
        reporting-holiday-col (collect-single-day (:reporting-holiday peeps) (:reporting-holiday-issue peeps) date)
        support-col (collect-single-day (:support peeps) (:support-issue peeps) date)
        v5-peeps-and-board [(:core peeps) 146]
        v5-col (collect-rand-story-from-board v5-peeps-and-board date)
        reporting-peeps-and-board [(:reporting peeps) 147]
        reporting-col (collect-rand-story-from-board reporting-peeps-and-board date)
        all-users-to-log (concat core-holiday-col
                                 reporting-holiday-col
                                 support-col
                                 v5-col
                                 reporting-col)]
    all-users-to-log))

(defn create-logs [args]
  (let [peeps run-data
        dates (if (:use-date-override peeps) (:date-override peeps)  [(t/today)])
        all-users-to-log (mapcat (fn [date] (collect-all-users peeps date)) dates)
        statuses (log-all-time all-users-to-log)
        all-good? (reduce (fn [ret [name status]] (and ret (= 201 status))) true statuses)]
    (if all-good?
      (println "All Good!")
      (println "!!!!!!!!!!!!!Some failed!!!!!!!!!!!!!!"))
    (println statuses)
  )
)

(defn foo [& args]
  (if (= "-h" (first args))
    (printhistory)
    (create-logs args)))
