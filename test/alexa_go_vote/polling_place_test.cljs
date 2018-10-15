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

(deftest intent-no-dialog-confirmed-full-address-success
  (testing "a confirmed full address performs a civic api lookup"
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
          event {:session
                 {:attributes {:full_address "345 Elm Drive 99999"}}
                 :request
                 {:dialogState "STARTED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:full_address {:name "full_address"
                                           :value "345 Elm St City ST 99999"
                                           :confirmationStatus "CONFIRMED"}
                            :street {:name "street"
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

(deftest intent-no-dialog-confirmed-full-address-no-results
  (testing "we don't go asking for state, just present no results"
    (let [query-fn (fn [_]
                     {:status 200
                      :body {}})
          process-fn (fn [req resp] (pp/process-response req resp))
          event {:session
                 {:attributes {:full_address "345 Elm Drive 99999"}}
                 :request
                 {:dialogState "STARTED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:full_address {:name "full_address"
                                           :value "345 Elm St City ST 99999"
                                           :confirmationStatus "CONFIRMED"}
                            :street {:name "street"
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
                     query-fn
                     process-fn
                     fail-if-called)]
      (is (= pp/no-info-response
             response)))))

(deftest intent-no-dialog-disconfirmed-address
  (testing "we fall back to starting the standard dialog"
    (let [event {:session
                 {:attributes {:full_address "345 Elm Drive 99999"}}
                 :request
                 {:dialogState "STARTED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:full_address {:name "full_address"
                                           :value "345 Elm St City ST 99999"
                                           :confirmationStatus "DENIED"}
                            :street {:name "street"
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
      (is (= pp/dialog-delegate-response
             response)))))

(deftest intent-disconfirmed-address-dialog-completed
  (testing "when the dialog completes we run the address even with disconfirmed full address"
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
          event {:session
                 {:attributes {:full_address "345 Elm Drive 99999"}}
                 :request
                 {:dialogState "COMPLETED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:full_address {:name "full_address"
                                           :value "345 Elm St City ST 99999"
                                           :confirmationStatus "DENIED"}
                            :street {:name "street"
                                     :value "345 Elm St"
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
      (is (= (str "Your polling place is at School. "
                             "The address is 123 Main St, City, 99999")
             (get-in response [:response :outputSpeech :text]))))))

(deftest intent-un-confirmed-full-address
  (testing "we ask to confirm the full address"
    (let [event {:session
                 {:attributes {:full_address "345 Elm St City ST 99999"}}
                 :request
                 {:dialogState "STARTED"
                  :intent {:name "pollingPlace"
                           :slots
                           {:full_address {:name "full_address"
                                           :value ""
                                           :confirmationStatus "NONE"}
                            :street {:name "street"
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
      (is (= (pp/confirm-full-address "345 Elm St City ST 99999")
             response)))))

;; This is where to start, we now want to try to lookup the device address one more
;; time and if we get access we go through the confirmation flow, otherwise
;; we go through the standard dialog
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
                        (callback nil))))))
  (testing "empty response"
    (is (= pp/dialog-delegate-response
           (pp/intent {:request {:dialogState "STARTED"}}
                      fail-if-called
                      fail-if-called
                      (fn [ctx callback]
                        (callback "")))))))

(deftest intent-dialog-started-address-success-case
  (testing "got an address from api"
    (let [address "345 Elm St City ST 99999"]
      (is (= (pp/confirm-full-address address)
             (pp/intent {:request {:dialogState "STARTED"}}
                        fail-if-called
                        fail-if-called
                        (fn [ctx callback]
                          (callback address))))))))
