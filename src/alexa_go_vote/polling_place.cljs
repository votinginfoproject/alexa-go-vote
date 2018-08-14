(ns alexa-go-vote.polling-place
  (:require [clojure.string :as str]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.nodejs :as node]
            [cljs-lambda.context :as ctx])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def dialog-delegate-response
  {:version 1.0
   :response {:directives [{:type "Dialog.Delegate"}]}})

(def server-error-response
  {:version 1.0
   :response {:outputSpeech
              {:type "PlainText"
               :text "I'm sorry, we're experiencing a server problem. Goodbye."
               :shouldEndSession true}}})

(defn response->clj
  [response]
  (as-> response $
    (:body $)
    (.toString $)
    (.parse js/JSON $)
    (js->clj $ :keywordize-keys true)))

(defn body->polling-place-info
  [body]
  (.log js/console (str "Response body: " (pr-str body)))
  (let [polling-locations (:pollingLocations body)
        polling-location (first polling-locations)
        address (get polling-location :address)
        name (get address :locationName)
        addr-comps (filter identity (map address [:line1 :line2 :city :zip]))
        addr-string (str/join ", " addr-comps)]
    [name addr-string]))

(defn process-response
  [response]
  (.log js/console "Civic API Response:" (pr-str response))
  (if (http/unexceptional-status? (:status response))
    (let [body (response->clj response)
          [name address] (body->polling-place-info body)]
      (.log js/console "Responding with polling location " name)
      {:version 1.0
       :response {:outputSpeech
                  {:type "PlainText"
                   :text (str "Your polling place is at " name ". The address is " address)
                   :shouldEndSession true}}})
    (do
      (.log js/console (str "Civic API Error: " (pr-str response)))
      server-error-response)))

(defn query-params
  [address]
  (let [env (-> js/process.env js/JSON.stringify js/JSON.parse js->clj)]
    (.log js/console (str "Environment: " (pr-str env)))
    {"address" address
     "key" (get env "CIVIC_API_KEY" nil)
     "productionDataOnly" (get env "PRODUCTION_DATA_ONLY" true)
     "returnAllAvailableData" true}))

(defn parse-request
  [request]
  (let [slots (get-in request [:intent :slots])
        street (get-in slots [:street :value])
        zip (get-in slots [:zip :value])
        address (str/join " " [street zip])]
    (.log js/console (str "parsed address is: " address))
    (query-params address)))

(def civic-url "https://www.googleapis.com:443/civicinfo/v2/voterinfo")

(defn live-query-fn
  [params]
  (http/get civic-url
            {:query-params params
             :accept "application/json"
             :content-type "application/json"}))

(defn live-process-fn
  [response-chan]
  (go (process-response (<! response-chan))))

(defn intent
  ([request] (intent request live-query-fn live-process-fn))
  ([request query-fn process-fn]
   (let [dialogState (:dialogState request)]
     (condp = dialogState
       "COMPLETED" (-> request
                       parse-request
                       query-fn
                       process-fn)
       dialog-delegate-response))))
