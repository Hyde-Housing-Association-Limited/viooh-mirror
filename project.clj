(def jackson "2.17.1")
(defproject com.viooh/viooh-mirror (-> "resources/viooh-mirror.version" slurp .trim)
  :description "Utility to mirror selected Kafka topics and their schemas across clusters."
  :url "https://github.com/VIOOH/viooh-mirror"


  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :repositories [["confluent" {:url "https://packages.confluent.io/maven/"}]]

  :managed-dependencies [[com.fasterxml.jackson.core/jackson-databind ~jackson]]
  :dependencies [[org.clojure/clojure "1.11.2"]

                 [samsara/trackit-core "0.9.3"]
                 [integrant "0.8.0"]
                 [http-kit "2.6.0"]
                 [cheshire "5.11.0"]
                 [metosin/compojure-api "1.1.13"
                  :exclusions [commons-fileupload/commons-fileupload]]
                 [slingshot "0.12.2"]

                 [com.brunobonacci/safely "0.7.0-alpha3"]
                 [com.brunobonacci/oneconfig "0.22.0"
                  :exclusions [samsara/trackit-core 
                               clj-commons/clj-yaml
                               com.cognitect.aws/api]]
                 
                 [clj-commons/clj-yaml               "1.0.27"]
                 [org.json/json "20231013"]
                 [org.apache.avro/avro   "1.11.4"]
                 [com.google.guava/guava  "32.0.0-jre"]
                 [com.cognitect.aws/api   "0.8.692"]
                 [io.netty/netty-codec   "4.1.68.Final"]


                 [com.viooh/kafka-ssl-helper "0.9.0"]

                 [fundingcircle/jackdaw "0.7.10"]
                 [io.confluent/kafka-schema-registry-client "5.4.1"
                  :exclusions [io.netty/netty-codec-http]]
                 
                 [io.netty/netty-codec-http "4.1.108.Final"]
                 [com.damnhandy/handy-uri-templates "2.1.8"
                  :exclusions [com.fasterxml.jackson.core/jacksonG-databind]]

                 ;;logging
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.4.14"]
                 [org.codehaus.janino/janino "3.1.3"] ;; logback configuration conditionals :(
                 [com.internetitem/logback-elasticsearch-appender "1.6"]

                 ;; observability
                 [com.brunobonacci/mulog               "0.8.1"]
                 [com.brunobonacci/mulog-elasticsearch "0.8.1"]
                 [com.brunobonacci/mulog-kafka         "0.8.1"]
                 [com.brunobonacci/mulog-cloudwatch    "0.8.1"
                  :exclusions [com.cognitect.aws/api]]
                 [com.brunobonacci/mulog-mbean-sampler "0.8.1"]
                 [com.brunobonacci/mulog-jvm-metrics   "0.8.1"]]

  :main viooh.mirror.main

  :global-vars {*warn-on-reflection* true}

  :profiles {:uberjar {:aot :all}
             :dev {:jvm-opts ["-D1config.default.backend=fs"]
                   :dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]]
                   :plugins      [[lein-midje "3.2.2"]]}})
