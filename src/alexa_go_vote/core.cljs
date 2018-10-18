(ns alexa-go-vote.core
  (:require [cljs-lambda.macros :refer-macros [deflambda]]
            [alexa-go-vote.address-api :as aa]
            [alexa-go-vote.logging :as log]
            [alexa-go-vote.polling-place :as pp]))

(defn standard-launch-response
  [ask-for-access?]
  {:version 1.0
   :response
   {:outputSpeech
    {:type "PlainText"
     :text (if ask-for-access?
             "Welcome to Voting Info! I can help you find your polling place. You can give me access to your Device Address in the Alexa app, or I can just ask you about the address you are registered to vote at. When you are ready, just ask me 'Where do I vote'?"
             "Welcome to Voting Info! I can help you find your polling place. Just ask me 'Where do I vote'?")}
    :shouldEndSession false}})

(defn launch-response
  "Attempts to get the address from the Device Address API, can succeed if this
  is a returning user. If we get back an address, store it in the session to be
  used once the user activates the polling place intent."
  [context]
  (aa/retrieve-address
   context
   (fn [address-response]
     (if (map? address-response)
       (merge (standard-launch-response false) {:sessionAttributes {:full_address address-response}})
       (if (= :not-authorized address-response)
         (assoc-in
          (standard-launch-response true)
          [:response :card]
          {:type "AskForPermissionsConsent"
           :permissions ["read::alexa:device:all:address"]})
         (standard-launch-response false))))))

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
     :text "I can help locate your polling place, just say 'Where do I vote' and I'll ask some questions about where you are registered to vote. I'll use your address to look up your polling place."}
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
  (let [{:keys [context request]} event
        type (:type request)
        intent (get-in request [:intent :name])]
    ;; If we ever offer more of our own intents than just pollingPlace
    ;; we should restructure this a bit more cleanly with a multimethod
   (cond
     (= type "LaunchRequest") (launch-response context)
     (= intent "pollingPlace") (pp/intent event)
     (= intent "AMAZON.HelpIntent") help-response
     (= intent "AMAZON.StopIntent") stop-response
     (= intent "AMAZON.CancelIntent") stop-response
     :else (fallback-response event))))
