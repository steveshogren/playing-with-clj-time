(ns jira-worklog.profiles)

(defn overwrite-dates [dates]
  (let [contents (read-string (slurp "data.clj"))
        contents (assoc contents :date-override dates)]
    contents))

;; (overwrite-dates ["test", "that"])
