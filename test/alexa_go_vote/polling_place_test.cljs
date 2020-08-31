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

(deftest intent-dialog-started-test
  (testing "started"
    (is (= pp/dialog-delegate-response
           (pp/intent {:dialogState "STARTED"}
                      fail-if-called
                      fail-if-called)))))

(deftest intent-dialog-in-progress-test
  (testing "in progress"
    (is (= pp/dialog-delegate-response
           (pp/intent {:dialogState "IN_PROGRESS"}
                      fail-if-called
                      fail-if-called)))))

(deftest intent-address-param-test
  (testing "a completed dialog constructs the address/query params"
    (pp/intent {:dialogState "COMPLETED"
                :intent
                {:slots
                 {:street {:value "123 Main St"}
                  :zip {:value "99999"}}}}
               (fn [params]
                 (is (= "123 Main St 99999"
                        (get params "address"))))
               (fn [_ _]))))

(deftest intent-success-response-test
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
          (pp/intent {:dialogState "COMPLETED"
                      :intent
                      {:slots
                       {:street {:value "123 Main St"}
                        :zip {:value "99999"}}}}
                     query-fn
                     process-fn)
          expected-text (str "Your polling place is at School. "
                             "The address is 123 Main St, City, 99999")
          card-text (str expected-text "\nFor more information, try your search at gettothepolls.org")]
      (is (= expected-text
             (get-in response [:response :outputSpeech :text])))
      (is (= card-text
             (get-in response [:response :card :text]))))))

(deftest intent-http-failure-response-test
  (testing "when we get back a non-successful http code"
    (let [query-fn (fn [_]
                     {:status 400})
          process-fn (fn [req resp] (pp/process-response req resp))
          response
          (pp/intent {:dialogState "COMPLETED"
                      :intent
                      {:slots
                       {:street {:value "123 Main St"}
                        :zip {:value "99999"}}}}
                     query-fn
                     process-fn)]
      (is (= pp/no-info-response
             response)))))

(deftest intent-needs-state-response-test
  (testing "an empty 200 response prompts request for state"
    (let [query-fn (fn [_]
                     {:status 200
                      :body {}})
          process-fn (fn [req resp] (pp/process-response req resp))
          request {:dialogState "COMPLETED"
                   :intent {:name "pollingPlace"
                            :slots
                            {:street {:name "street"
                                      :value "123 Main St"
                                      :confirmationStatus "CONFIRMED"}
                             :state {:name "state"
                                     :confirmationStatus "NONE"}
                             :zip {:name "zip"
                                   :value "99999"
                                   :confirmationStatus "CONFIRMED"}}}}
          response
          (pp/intent request
                     query-fn
                     process-fn)]
      (is (= (pp/request-state request)
             response)))))

(deftest intent-state-still-not-enough-response-test
  (testing "an empty 200 response prompts request for state"
    (let [query-fn (fn [_]
                     {:status 200
                      :body {}})
          process-fn (fn [req resp] (pp/process-response req resp))
          request {:dialogState "COMPLETED"
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
                                   :confirmationStatus "CONFIRMED"}}}}
          response
          (pp/intent request
                     query-fn
                     process-fn)]
      (is (= pp/no-info-response
             response)))))
