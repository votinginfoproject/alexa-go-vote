(defproject alexa-go-vote "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "http://please.FIXME"
  :dependencies [[org.clojure/clojure       "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [io.nervous/cljs-lambda    "0.3.5"]
                 [com.github.tank157/cljs-http-node "fix-query-string"
                  :exclusions [commons-codec]]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-npm       "0.6.2"]
            [lein-doo       "0.1.7"]
            [io.nervous/lein-cljs-lambda "0.6.6"]]
  :repositories [["jitpack" "https://jitpack.io"]]
  :npm {:dependencies [[source-map-support "0.4.0"]]}
  :source-paths ["src"]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :doo {:build "test"}
  :aliases {"test" ["doo" "node" "once"]}
  :profiles
  {:dev-overrides {}
   :dev
   [:dev-overrides
    {:dependencies [[com.cemerick/piggieback "0.2.2"]
                    [cljs-node-io "0.5.0"]]

     :plugins      [[org.bodil/lein-noderepl "0.1.11"]]}]}
  :cljs-lambda
  {:defaults      {:role "arn:aws:iam::858394542481:role/cljs-lambda-default"}
   :resource-dirs ["static"]
   :functions
   [{:name   "alexa-go-vote-magic"
     :invoke alexa-go-vote.core/alexa-go-vote-magic}]}
  :cljsbuild
  {:builds [{:id "alexa-go-vote"
             :source-paths ["src"]
             :compiler {:output-to     "target/alexa_go_vote/alexa-go-vote.js"
                        :output-dir    "target/alexa_go_vote"
                        :source-map    "target/alexa_go_vote/alexa-go-vote.js.map"
                        :target        :nodejs
                        :language-in   :ecmascript5
                        :optimizations :advanced}}
            {:id "test"
             :source-paths ["src" "test"]
             :compiler {:output-to     "target/alexa_go_vote_test/alexa-go-vote.js"
                        :output-dir    "target/alexa_go_vote_test"
                        :target        :nodejs
                        :language-in   :ecmascript5
                        :optimizations :none
                        :main          alexa-go-vote.test-runner}}]})
