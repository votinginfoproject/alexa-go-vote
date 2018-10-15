(ns alexa-go-vote.test-runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [alexa-go-vote.core-test]
              [alexa-go-vote.address-api-test]
              [alexa-go-vote.polling-place-test]))

(doo-tests 'alexa-go-vote.core-test
           'alexa-go-vote.address-api-test
           'alexa-go-vote.polling-place-test)
