(ns alexa-go-vote.address-api-test
  (:require [alexa-go-vote.address-api :as aa]
            [cljs.test :refer-macros [deftest is testing async]]
            [cljs.core.async :refer [<!] :refer-macros [go]]))

(deftest process-response-test
  (testing "success"
    (let [device-address {:addressLine1 "123 Main St"
                          :addressLine2 "Unit 3"
                          :addressLine3 ""
                          :city "City"
                          :stateOrRegion "ST"
                          :postalCode "99999"}
          response {:status 200
                    :body (->> device-address 
                               clj->js
                               (.stringify js/JSON))}]
      (aa/process-response response
                           (fn [address]
                             (is (= address
                                    device-address))))))
  (testing "not authorized"
    (let [response {:status 403
                    :body {}}]
      (aa/process-response response
                           (fn [address]
                             (is (= address
                                    :not-authorized)))))))
