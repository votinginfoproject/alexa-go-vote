(ns alexa-go-vote.address-api
  (:require [alexa-go-vote.logging :as log]
            [alexa-go-vote.util :refer [response-body->clj]]
            [clojure.string :as str]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cljs.nodejs :as node]
            [cljs-lambda.context :as ctx])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn address-api-url
  "Constructs a URL to request a Device Address"
  [device-id]
  (str "https://api.amazonalexa.com/v1/devices/" device-id
       "/settings/address"))

(defn extract-address
  [{:keys [addressLine1 city stateOrRegion postalCode]}]
  (str/join " " (remove str/blank? [addressLine1 city
                                    stateOrRegion postalCode])))

(defn process-response
  [response callback]
  (let [body (response-body->clj (:body response))
        status (:status response)]
    (when-not (= 200 status)
      (log/error "Device Address API Response Not Success: " (pr-str response)))
    (condp = status
      200 (callback (extract-address body))
      204 (callback :no-address-available)
      403 (callback :not-authorized)
      405 (callback :not-supported)
      429 (callback :over-request-limit)
      500 (callback :server-error)
      (callback :unknown-status))))

(defn live-query-fn
  "Queries the Amazon Address API, returning an async channel with the
  response"
  [device-id api-access-token]
  (http/get (address-api-url device-id)
            {:headers {"Authorization" (str "Bearer " api-access-token)}
             :accept "application/json"}))

(defn live-process-fn
  "Processes the response on the response channel, sending
  the results to the callback and wraps it all in a channel
  suitable to respond with."
  [response-chan callback]
  (go (process-response (<! response-chan) callback)))

(defn retrieve-address
  ([context callback]
   (retrieve-address context callback live-query-fn live-process-fn))
  ([context callback query-fn process-fn]
   (let [device-id (get-in context [:System :device :deviceId])
         api-access-token (get-in context [:System] :apiAccessToken)]
     (-> (query-fn device-id api-access-token)
         (process-fn callback)))))
