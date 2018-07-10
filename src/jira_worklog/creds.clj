(ns jira-worklog.creds
  (:require [environ.core :refer [env]]
            [com.ashafa.clutch :as c]
            )
  )

(def db-name (env :couchdburl))

(defn get-creds [name]
  (or (:up (c/get-document db-name name))
      (throw (Exception. (str "User: '" name "' not found in couchdb")))))

(comment
  (c/bulk-update db-name [
                          { :_id "jimham" :up "test"}
                          ]))


;; (c/put-document db-name (assoc (c/get-document db-name "shogren1") :name "horse"))

