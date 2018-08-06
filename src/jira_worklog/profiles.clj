(ns jira-worklog.profiles)

(defn overwrite-dates [dates]
  (let [contents (read-string (slurp "data.clj"))
        contents (assoc contents :date-override dates
                                 :use-date-override true)]
    (clojure.pprint/pprint contents (clojure.java.io/writer "data_out.clj"))))

;; (overwrite-dates ["test", "that"])
