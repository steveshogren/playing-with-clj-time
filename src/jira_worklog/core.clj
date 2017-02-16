(ns jira-worklog.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [jira-worklog.post :as p]))

(defn get-data []
  (read-string (slurp "data.clj")))

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

(comment
  (choose-sprint [{:id 11 :name "test"} {:id 22 :name "other"}])
  )
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
                      (contains? (:filterStatus (get-data)) (:statusName %))
                      (= "done" (:status %)))) all-from-sprint)))

(comment
  (get-logs 146)
  )
(defn get-logs [board]
  (let [stories (get-stories board)
        logs (mapcat (comp p/query :id) stories)]
    logs))

(defn get-story-peep-pairs [peeps board]
  (let [stories (get-stories board)]
    (map (fn [peep]
           (let [story (rand-nth stories)]
             [(:id story) peep (:desc story)])) peeps)))

(defn confirm-logs? [stories-and-peeps time-f]
  (doseq [[story peep desc] stories-and-peeps]
    (println peep " - " (time-f) " - " desc ))
  (println "Submit logs? y/n")
  (= "y" (read-line)))

(defn log-time [[peeps board] time-f]
  (let [stories-and-peeps (get-story-peep-pairs peeps board)]
    (if (and (< 0 (count stories-and-peeps))
             (confirm-logs? stories-and-peeps time-f))
      (reduce (fn [r [story peep desc]]
                (let [result [peep (create (env peep) (time-f) story)]]
                  (conj r result)))
              []
              stories-and-peeps))))

(defn log-day [peeps-n-board]
  (concat (log-time peeps-n-board today-8am)
          (log-time peeps-n-board today-1pm)))

(defn log-single-day [peeps story]
  (reduce (fn [r peep]
            (if (confirm-logs? [[story peep story]] today-8am)
              (let [result [peep (create (env peep) (today-8am) story eight-hours)]]
                (conj r result))
              r))
          []
          peeps))

(defn foo [& args]
  (if (= "--dry-run" (first args))
    (swap! p/dry? (fn [x] true)))
  (let [peeps (get-data)
        v5 [(:core peeps) 146]
        core-holiday (log-single-day (:core-holiday peeps) (:core-holiday-issue peeps))
        reporting-holiday (log-single-day (:reporting-holiday peeps) (:reporting-holiday-issue peeps))
        support (log-single-day (:support peeps) (:support-issue peeps))
        ;;web [(:web peeps) 0]
        reporting [(:reporting peeps) 147]
        statuses (concat core-holiday
                         reporting-holiday
                         support
                         (log-day v5)
                         (log-day reporting))
        all-good? (reduce (fn [ret [name status]] (and ret (= 201 status))) true statuses)]
    (if all-good?
      (println "All Good!")
      (println "Some failed!"))
    (println statuses)))
