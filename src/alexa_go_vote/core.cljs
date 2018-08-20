(ns alexa-go-vote.core
  (:require [cljs-lambda.macros :refer-macros [deflambda]]
            [alexa-go-vote.logging :as log]
            [alexa-go-vote.polling-place :as pp]))

(def launch-response
  {:version 1.0
   :response {:outputSpeech {:type "PlainText" :text "Welcome to Go Vote! I can help you find your polling place. Just ask me 'Where do I vote'?"}
              :shouldEndSession false}})

(defn fallback-response [evt]
  (log/error "default response enacted for event:" (pr-str evt))
  {:version 1.0
   :response {:outputSpeech {:type "PlainText" :text "I'm sorry, I didn't quite understand that."}
              :shouldEndSession false}})

(def help-response
  {:version 1.0
   :response
   {:outputSpeech
    {:type "PlainText"
     :text "I can help locate your polling place, just say 'Where do I vote' and I'll ask some questions about where you are registered to vote. I'll use your address to look up your polling place, and don't worry, I'll never store your address."}
    :shouldEndSession false}})

(def stop-response
  {:version 1.0
   :response
   {:outputSpeech
    {:type "PlainText"
     :text "Ok, goodbye!"}
    :shouldEndSession true}})

(deflambda alexa-go-vote-magic
  "This function receives the JSON input from the Alexa function and
   creates a map of the items of interest.  You can then dispatch on
   the input map to call functions to build the proper response map"
  [event ctx]
  (log/debug "Incoming Event:" (pr-str event))
  (let [{:keys [session request
                version timestamp]} event
        type (:type request)
        intent (get-in request [:intent :name])]
   (cond
     (= type "LaunchRequest") launch-response
     (= intent "pollingPlace") (pp/intent request)
     (= intent "AMAZON.HelpIntent") help-response
     (= intent "AMAZON.StopIntent") stop-response
     (= intent "AMAZON.CancelIntent") stop-response
     :else (fallback-response event))))
