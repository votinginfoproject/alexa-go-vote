(ns alexa-go-vote.logging
  (:require [clojure.string :as str]))

(defn log [strings]
  (.log js/console (str/join " " strings)))

(defn debug? []
  (-> js/process.env
      js/JSON.stringify
      js/JSON.parse
      js->clj
      (get "DEBUG" false)))

(defn debug
  "If debug logging is turned on will log the strings, adding
  a space between parts and putting `DEBUG:` in front."
  [& strings]
  (when (debug?)
    (log (concat ["DEBUG:"] strings))))

(defn error
  "Logs the error strings, putting `ERROR:` in front."
  [& strings]
  (log (concat ["ERROR:"] strings)))
