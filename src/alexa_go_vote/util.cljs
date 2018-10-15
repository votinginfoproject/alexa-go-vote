(ns alexa-go-vote.util)

(defn response-body->clj
  [body]
  (as-> body $
    (.toString $)
    (.parse js/JSON $)
    (js->clj $ :keywordize-keys true)))
