(ns alexa-go-vote.test-runner
    (:require [doo.runner :refer-macros [doo-all-tests]]
              [alexa-go-vote.core-test]
              [alexa-go-vote.polling-place-test]))

(doo-all-tests #"^alexa-go-vote\..+-test$")
