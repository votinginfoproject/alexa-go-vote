(defproject alexa-go-vote "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "http://please.FIXME"
  :dependencies [[org.clojure/clojure       "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [io.nervous/cljs-lambda    "0.3.5"]
                 [com.github.tank157/cljs-http-node "fix-query-string"
                  :exclusions [commons-codec]]
                 [org.clojure/core.async "1.3.610"]]
  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-npm       "0.6.2"]
            [lein-doo       "0.1.10"]
            [io.nervous/lein-cljs-lambda "0.6.6"]]
  :repositories [["jitpack" "https://jitpack.io"]]
  :npm {:dependencies [[source-map-support "0.4.0"]]}
  :source-paths ["src"]
  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
  :doo {:build "test"}
  :aliases {"test" ["doo" "node" "once"]}
  :profiles
  {:dev-overrides {}
   :dev
   [:dev-overrides
    {:dependencies [[cider/piggieback "0.5.1"]
                    [cljs-node-io "1.1.2"]]

     :plugins      [[org.bodil/lein-noderepl "0.1.11"]]}]}
  :cljs-lambda
  {:defaults      {:role ~(System/getenv "AWS_ROLE")}
   :resource-dirs ["static"]
   :region us-east-1
   :functions
   [{:name   "alexa-go-vote-magic"
     :create true
     :invoke alexa-go-vote.core/alexa-go-vote-magic
     :env {"CIVIC_API_KEY" ~(System/getenv "CIVIC_API_KEY")
           "PRODUCTION_DATA_ONLY" ~(System/getenv "PRODUCTION_DATA_ONLY")}}]}
  :cljsbuild
  {:builds [{:id "alexa-go-vote"
             :source-paths ["src"]
             :compiler {:output-to     "target/alexa_go_vote/alexa-go-vote.js"
                        :output-dir    "target/alexa_go_vote"
                        :source-map    "target/alexa_go_vote/alexa-go-vote.js.map"
                        :target        :nodejs
                        :language-in   :ecmascript6
                        :optimizations :advanced}}
            {:id "test"
             :source-paths ["src" "test"]
             :compiler {:output-to     "target/alexa_go_vote_test/alexa-go-vote.js"
                        :output-dir    "target/alexa_go_vote_test"
                        :target        :nodejs
                        :language-in   :ecmascript6
                        :optimizations :none
                        :main          alexa-go-vote.test-runner}}]})
