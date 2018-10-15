(ns alexa-go-vote.address-api-test
  (:require [alexa-go-vote.address-api :as aa]
            [cljs.test :refer-macros [deftest is testing async]]
            [cljs.core.async :refer [<!] :refer-macros [go]]))

(deftest extract-address-test
  (testing "full extraction"
    (is (= "123 Main St Denver CO 80202"
           (aa/extract-address {:addressLine1 "123 Main St"
                                :addressLine2 "Unit B"
                                :city "Denver"
                                :stateOrRegion "CO"
                                :postalCode "80202"})))))

(deftest process-response-test
  (testing "success"
    (let [response {:status 200
                    :body (->> {:addressLine1 "123 Main St"
                                :addressLine2 "Unit 3"
                                :addressLine3 ""
                                :city "City"
                                :stateOrRegion "ST"
                                :postalCode "99999"}
                                 clj->js
                                 (.stringify js/JSON))}]
      (aa/process-response response
                           (fn [address]
                             (is (= address
                                    "123 Main St City ST 99999"))))))
  (testing "not authorized"
    (let [response {:status 403
                    :body {}}]
      (aa/process-response response
                           (fn [address]
                             (is (= address
                                    :not-authorized)))))))
