(ns alexa-go-vote.polling-place
  (:require [clojure.string :as str]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.nodejs :as node]
            [cljs-lambda.context :as ctx])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn dialog-delegate
  []
  (.log js/console "Responding with Dialog.Delegate")
  {:version 1.0
   :response {:directives [{:type "Dialog.Delegate"}]}})

(defn server-error []
  (.log js/console "Responding with server-error")
  {:version 1.0
   :response {:outputSpeech
              {:type "PlainText"
               :text "I'm sorry, we're experiencing a server problem. Goodbye."
               :shouldEndSession true}}})

;; (query-polling-place "3632  INTERLAKE AVE N SEATTLE WA 98103")

(defn process-response
  [response]
  (if (http/unexceptional-status? (:status response))
    (let [body-json (->> response :body .toString (.parse js/JSON))
          body (js->clj body-json :keywordize-keys true)
          polling-locations (:pollingLocations body)
          polling-location (first polling-locations)
          address (get polling-location :address)
          name (get address :locationName)
          address (str/join ", " [(get address :line1) (get address :line2)
                                  (get address :city) (get address :zip)])]
      (.log js/console "Responding with polling location " name)
      {:version 1.0
       :response {:outputSpeech
                  {:type "PlainText"
                   :text (str "Your polling place is at " name ". The address is " address)
                   :shouldEndSession true}}})
    (do
      (.log js/console (pr-str response))
      (server-error))))

(defn query-polling-place
  [address]
  (let [env (-> js/process.env js/JSON.stringify js/JSON.parse js->clj)
        civic-url "https://www.googleapis.com:443/civicinfo/v2/voterinfo"
        params    {"address" address
                   "key" (get env "CIVIC_API_KEY" nil)
                   "productionDataOnly" false
                   "returnAllAvailableData" true}]
    (go (let [response (<! (http/get civic-url
                                     {:query-params params
                                      :accept "application/json"
                                      :content-type "application/json"}))]
          (process-response response)))))

(defn polling-place-response
  [request]
  (let [slots (get-in request [:intent :slots])
        street (get-in slots [:street :value])
        city (get-in slots [:city :value])
        state (get-in slots [:state :value])
        zip (get-in slots [:zip :value])
        address (str/join " " [street city state zip])]
    (.log js/console (str "Getting polling place for " address))
    (query-polling-place address)))

(defn intent
  [session request]
  (let [dialogState (:dialogState request)]
    (condp = dialogState
      "COMPLETED" (polling-place-response request)
      (dialog-delegate))))
