(ns alexa-go-vote.polling-place-test
  (:require [alexa-go-vote.polling-place :as pp]
            [cljs.test :refer-macros [deftest is testing async]]
            [cljs.core.async :refer [<!] :refer-macros [go]]))

(deftest body->polling-place-info-test
  (testing "returns a name and full address"
    (is (= ["School" "123 Main St, Entrance C, City, Zip"]
           (pp/body->polling-place-info
            {:pollingLocations
             [{:address {:locationName "School"
                         :line1 "123 Main St"
                         :line2 "Entrance C"
                         :city "City"
                         :state "ST"
                         :zip "Zip"}}]}))))
  (testing "skips space for line2 when not present"
    (is (= ["School" "123 Main St, City, Zip"]
           (pp/body->polling-place-info
            {:pollingLocations
             [{:address {:locationName "School"
                         :line1 "123 Main St"
                         :city "City"
                         :state "ST"
                         :zip "Zip"}}]})))))

(defn fail-if-called [& params]
  (throw (js/Error.
          (str "Query fn should not have been called"
               (pr-str params)))))

(deftest intent-dialog-in-progress-test
  (testing "in progress"
    (is (= pp/dialog-delegate-response
           (pp/intent {:request {:dialogState "IN_PROGRESS"}}
                      fail-if-called
                      fail-if-called
                      fail-if-called)))))

(deftest intent-dialog-complete-address-param-test
  (testing "a completed dialog constructs the address/query params"
    (pp/intent {:request {:dialogState "COMPLETED"
                          :intent
                          {:slots
                           {:street {:value "123 Main St"}
                            :zip {:value "99999"}}}}}
               (fn [params]
                 (is (= "123 Main St 99999"
                        (get params "address"))))
               (fn [_ _])
               fail-if-called))
  (testing "and include the optional state when available"
    (pp/intent {:request {:dialogState "COMPLETED"
                          :intent
                          {:slots
                           {:street {:value "123 Main St"}
                            :state {:value "AK"}
                            :zip {:value "99999"}}}}}
               (fn [params]
                 (is (= "123 Main St AK 99999"
                        (get params "address"))))
               (fn [_ _])
               fail-if-called)))

(deftest intent-dialog-complete-success-response-test
  (testing "processes a successful response...successfully"
    (let [query-fn (fn [_]
                     {:status 200
                      :body (->> {:pollingLocations
                                  [{:address
                                    {:locationName "School"
                                     :line1 "123 Main St"
                                     :city "City"
                                     :zip "99999"}}]}
                                 clj->js
                                 (.stringify js/JSON))})
          process-fn (fn [req resp] (pp/process-response req resp))
          response
          (pp/intent {:request
                      {:dialogState "COMPLETED"
                       :intent
                       {:slots
                        {:street {:value "123 Main St"}
                         :zip {:value "99999"}}}}}
                     query-fn
                     process-fn
                     fail-if-called)
          expected-text (str "Your polling place is at School. "
                             "The address is 123 Main St, City, 99999")
          card-text (str expected-text "\nFor more information, try your search at icantfindmypollingplace.com")]
      (is (= expected-text
             (get-in response [:response :outputSpeech :text])))
      (is (= card-text
             (get-in response [:response :card :text]))))))

(deftest intent-dialog-complete-http-failure-response-test
  (testing "when we get back a non-successful http code"
    (let [query-fn (fn [_]
                     {:status 400})
          process-fn (fn [req resp] (pp/process-response req resp))
          response
          (pp/intent {:request
                      {:dialogState "COMPLETED"
                       :intent
                       {:slots
                        {:street {:value "123 Main St"}
                         :zip {:value "99999"}}}}}
                     query-fn
                     process-fn
                     fail-if-called)]
      (is (= pp/no-info-response
             response)))))

(deftest intent-dialog-complete-needs-state-response-test
  (testing "an empty 200 response prompts request for state when in dialog"
    (let [query-fn (fn [_]
                     {:status 200
                      :body {}})
          process-fn (fn [req resp] (pp/process-response req resp))
          event {:request
                 {:dialogState "COMPLETED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:street {:name "street"
                                     :value "123 Main St"
                                     :confirmationStatus "CONFIRMED"}
                            :state {:name "state"
                                    :value ""
                                    :confirmationStatus "NONE"}
                            :zip {:name "zip"
                                  :value "99999"
                                  :confirmationStatus "CONFIRMED"}}}}}
          response
          (pp/intent event
                     query-fn
                     process-fn
                     fail-if-called)]
      (is (= (pp/request-state (:request event))
             response)))))

(deftest intent-dialog-complete-state-still-not-enough-response-test
  (testing "a no data response when we tried all possibilities in dialog"
    (let [query-fn (fn [_]
                     {:status 200
                      :body {}})
          process-fn (fn [req resp] (pp/process-response req resp))
          event {:request
                 {:dialogState "COMPLETED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:street {:name "street"
                                     :value "123 Main St"
                                     :confirmationStatus "CONFIRMED"}
                            :state {:name "state"
                                    :value "State"
                                    :confirmationStatus "NONE"}
                            :zip {:name "zip"
                                  :value "99999"
                                  :confirmationStatus "CONFIRMED"}}}}}
          response
          (pp/intent event
                     query-fn
                     process-fn
                     fail-if-called)]
      (is (= pp/no-info-response
             response)))))

(deftest intent-un-confirmed-full-address
  (testing "we ask to confirm the full address"
    (let [address {:addressLine1 "345 Elm St"
                   :addressLine2 ""
                   :city "City"
                   :stateOrRegion "AK"
                   :postalCode "99999"}
          event {:session
                 {:attributes {:full_address address}}
                 :request
                 {:dialogState "STARTED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:street {:name "street"
                                     :value ""
                                     :confirmationStatus "NONE"}
                            :state {:name "state"
                                    :value ""
                                    :confirmationStatus "NONE"}
                            :zip {:name "zip"
                                  :value ""
                                  :confirmationStatus "NONE"}}}}}
          response
          (pp/intent event
                     fail-if-called
                     fail-if-called
                     fail-if-called)]
      (is (= (pp/confirm-full-address address)
             response)))))

(deftest intent-dialog-started-address-fail-cases
  (testing "unauthorized"
    (is (= pp/dialog-delegate-response
           (pp/intent {:request {:dialogState "STARTED"}}
                      fail-if-called
                      fail-if-called
                      (fn [ctx callback]
                        (callback :not-authorized))))))
  (testing "no-address-available"
    (is (= pp/dialog-delegate-response
           (pp/intent {:request {:dialogState "STARTED"}}
                      fail-if-called
                      fail-if-called
                      (fn [ctx callback]
                        (callback :no-address-available))))))
  (testing "nil response"
    (is (= pp/dialog-delegate-response
           (pp/intent {:request {:dialogState "STARTED"}}
                      fail-if-called
                      fail-if-called
                      (fn [ctx callback]
                        (callback nil)))))))

(deftest intent-dialog-started-address-success-case
  (testing "got an address from api"
    (let [address {:addressLine1 "123 Main St"
                   :addressLine2 "Unit B"
                   :city "City"
                   :stateOrRegion "AK"
                   :postalCode "99999"}]
      (is (= (pp/confirm-full-address address)
             (pp/intent {:request {:dialogState "STARTED"}}
                        fail-if-called
                        fail-if-called
                        (fn [ctx callback]
                          (callback address))))))))
