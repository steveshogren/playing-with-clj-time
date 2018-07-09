(defproject jira-worklog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-environ "1.1.0"]]
  :main jira-worklog.core/foo
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-http "2.3.0"]
                 [com.ashafa/clutch "0.4.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [clj-time "0.12.2"]
                 [org.clojure/data.json "0.2.6"]
                 [environ "1.1.0"]])
