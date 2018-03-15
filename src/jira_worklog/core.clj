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

(defn today-8am-date []
  (if-let [d (f/parse (:date-override (get-data)))]
    (t/plus d (t/hours 7))
    (t/today-at 7 0 0)))

(defn today-8am []
  (myformat (today-8am-date)))

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

(defn get-story-peep-pairs [[peeps board] time-f]
  (let [stories (get-stories board)]
    (map (fn [peep]
           (let [story (rand-nth stories)]
             [(:id story) peep (:desc story) time-f]))
         peeps)))

(defn confirm-logs? [stories-and-peeps]
  (doseq [[story peep desc time-f] stories-and-peeps]
    (println peep " - " (time-f) " - " desc ))
  (println "Submit logs? y/n")
  (= "y" (read-line)))

(defn log-all-time [stories-and-peeps-and-time-f]
  (if (and (< 0 (count stories-and-peeps-and-time-f))
           (confirm-logs? stories-and-peeps-and-time-f))
    (reduce (fn [r [story peep desc time-f]]
              (let [result [peep (create (env peep) (time-f) story)]]
                (conj r result)))
            []
            stories-and-peeps-and-time-f)
    (println "Logging Aborted! No logs posted.")))

(defn collect-split-day [peeps-n-board]
  (concat (get-story-peep-pairs peeps-n-board today-8am)
          (get-story-peep-pairs peeps-n-board today-1pm)))

(defn collect-single-day [peeps story]
  (map (fn [peep] [story peep story today-8am]) peeps))

(defn printhistory []
  (println "history incoming!")
  (println (p/log-dates)))

(defn create-logs [args]
  (if (= "--dry-run" (first args))
    (swap! p/dry? (fn [x] true)))
  (let [peeps (get-data)
        core-holiday-col (collect-single-day (:core-holiday peeps) (:core-holiday-issue peeps))
        reporting-holiday-col (collect-single-day (:reporting-holiday peeps) (:reporting-holiday-issue peeps))
        support-col (collect-single-day (:support peeps) (:support-issue peeps))
        v5-peeps-and-board [(:core peeps) 146]
        v5-col (collect-split-day v5-peeps-and-board)
        reporting-peeps-and-board [(:reporting peeps) 147]
        reporting-col (collect-split-day reporting-peeps-and-board)
        ;;web [(:web peeps) 0]
        statuses (log-all-time (concat core-holiday-col
                                       reporting-holiday-col
                                       support-col
                                       v5-col
                                       reporting-col))
        all-good? (reduce (fn [ret [name status]] (and ret (= 201 status))) true statuses)]
    (if all-good?
      (do (println "All Good!")
          (spit "dates.txt" (str "\n" (today-8am)) :append true))
      (println "!!!!!!!!!!!!!Some failed!!!!!!!!!!!!!!"))
    (println statuses))
  )

(defn foo [& args]
  (if (= "-h" (first args))
    (printhistory)
    (create-logs args)))
