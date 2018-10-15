(ns alexa-go-vote.polling-place
  (:require [alexa-go-vote.logging :as log]
            [alexa-go-vote.util :refer [response-body->clj]]
            [clojure.string :as str]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.nodejs :as node]
            [cljs-lambda.context :as ctx])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def dialog-delegate-response
  {:version 1.0
   :response {:directives [{:type "Dialog.Delegate"}]}})

(def no-info-response
  {:version 1.0
   :response
   {:outputSpeech
    {:type "PlainText"
     :text (str "I'm sorry, I couldn't find a result for that address. "
                "Check with your local election official, or try again "
                "closer to the election.")}
    :card
    {:type "Standard"
     :title "No Results"
     :text (str "You can try your search again at icantfindmypollingplace.com")}
    :shouldEndSession true}})

(defn response->clj
  [response]
  (as-> response $
    (:body $)
    (.toString $)
    (.parse js/JSON $)
    (js->clj $ :keywordize-keys true)))

(defn body->polling-place-info
  [{:keys [pollingLocations] :as body}]
  (log/debug "Response body:" (pr-str body))
  (let [address (:address (first pollingLocations))]
    [(:locationName address)
     (str/join ", " (keep identity (map address [:line1 :line2 :city :zip])))]))

(defn polling-place-card
  [text]
  {:type "Standard"
   :title "Your Polling Place"
   :text (str text "\nFor more information, try your search at icantfindmypollingplace.com")})

(defn first-polling-place-response
  [body]
  (let [[name address] (body->polling-place-info body)]
    (log/debug "Responding with polling location:" name)
    (let [text (str "Your polling place is at " name
                    ". The address is " address)]
      {:version 1.0
       :response {:outputSpeech
                  {:type "PlainText"
                   :text text}
                  :card (polling-place-card text)
                  :shouldEndSession true}})))

(defn request-state
  "Request the optional state slot value since apparently we couldn't
  get a Civic API match with just address and zip"
  [request]
  {:version 1.0
   :response
   {:outputSpeech
    {:type "PlainText"
     :text "What state are you registered to vote in?"}
    :shouldEndSession false
    :directives [{:type "Dialog.ElicitSlot"
                  :slotToElicit "state"
                  :updatedIntent (:intent request)}]}})

(defn process-response
  [request response]
  (if (http/unexceptional-status? (:status response))
    (let [body (response-body->clj (:body response))]
      (if (contains? body :pollingLocations)
        (first-polling-place-response body)
        (if (str/blank? (get-in request [:intent :slots :state :value] nil))
          (request-state request)
          no-info-response)))
    (do
      (log/debug "Civic API Error:" (pr-str response))
      no-info-response)))

(defn query-params
  [address]
  (let [env (-> js/process.env js/JSON.stringify js/JSON.parse js->clj)]
    {"address" address
     "key" (get env "CIVIC_API_KEY" nil)
     "productionDataOnly" (get env "PRODUCTION_DATA_ONLY" true)
     "returnAllAvailableData" true}))

(defn parse-request
  [request]
  (let [slots (get-in request [:intent :slots])
        street (get-in slots [:street :value])
        state (get-in slots [:state :value])
        zip (get-in slots [:zip :value])
        address (str/join " " (keep identity [street state zip]))]
    (log/debug "Parsed request address is:" address)
    (query-params address)))

(def civic-url "https://www.googleapis.com:443/civicinfo/v2/voterinfo")

(defn live-query-fn
  [params]
  (http/get civic-url
            {:query-params params
             :accept "application/json"
             :content-type "application/json"}))

(defn live-process-fn
  [request response-chan]
  (go (process-response request (<! response-chan))))

(defn intent
  ([request] (intent request live-query-fn live-process-fn))
  ([request query-fn process-fn]
   (let [dialogState (:dialogState request)]
     (condp = dialogState
       "COMPLETED" (->> request
                        parse-request
                        query-fn
                        (process-fn request))
       dialog-delegate-response))))
