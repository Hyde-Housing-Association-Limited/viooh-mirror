;;
;; Copyright 2019-2020 VIOOH Ltd
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
(ns viooh.mirror.schema-registry
  (:require [clojure.string :as str]
            [safely.core :refer [safely]]
            [clojure.walk :as walk])
  (:import
   [io.confluent.kafka.schemaregistry.client.rest.exceptions RestClientException]
   [io.confluent.kafka.schemaregistry.client SchemaRegistryClient CachedSchemaRegistryClient]
   [org.apache.avro Schema]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;        ----==| S C H E M A   R E G I S T R Y   C L I E N T |==----         ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defprotocol ToClojure
  (->clj [x] "Convert x to Clojure data types"))



(extend-protocol ToClojure

  io.confluent.kafka.schemaregistry.client.SchemaMetadata
  (->clj [^SchemaMetadata sm]
    {:id      (.getId sm)
     :version (.getVersion sm)
     :schema  (.getSchema sm)})

  nil
  (->clj [_] nil))



(def ^{:arglists '([url] [url configs] [url configs capacity])
       :doc "It creates a CachedSchemaRegistry client and it caches the instance."
       :tag CachedSchemaRegistryClient}
  schema-registry
  (memoize
   (fn schema-registry-direct
     ([^String url]
      (schema-registry-direct url {}))
     ([^String url ^java.util.Map configs]
      (schema-registry-direct url 256 configs))
     ([^String url ^long capacity ^java.util.Map configs]
      {:client (CachedSchemaRegistryClient. url capacity
                                            (or (walk/stringify-keys configs) {}))
       :url    url}))))



(defmacro return-nil-when-not-found
  {:indent/style [0]}
  [& body]
  `(try
     ~@body
     (catch RestClientException x#
       ;; `getErrorCode` returns error codes
       ;; like: 40401, 40402, 40403, 40408
       (when-not (= 404 (quot (or (.getErrorCode x#) 0) 100))
         (throw x#)))))



(def ^:private endpoint-key
  (memoize
   (fn
     [{:keys [url] :as client}]
     (when url
       (-> url
         (str/replace-first #"^https?://" "")
         (str/replace #"[^\w\d]" "_")
         (str/replace #"_+$" "")
         (keyword))))))



(defmacro with-circuit-breaker
  "Wraps the call wit a different circuit breaker for every endpoint"
  {:indent/style [0]}
  [^String url & body]
  `(let [url# ~url]
     (safely
      ~@body
      :on-error
      :max-retries 5
      :message (str "Error while processing request for schema-registry: " url#)
      :circuit-breaker (endpoint-key url#)
      :timeout 5000)))



(defn subjects
  "Returns all the subjects registered int he schema registry"
  [{:keys [url client]}]
  (with-circuit-breaker url
    (.. ^SchemaRegistryClient client
        (getAllSubjects))))



(defn versions
  "Returns all the versions of a given subject"
  [{:keys [url client]} ^String subject]
  (with-circuit-breaker url
    (return-nil-when-not-found
     (.. ^SchemaRegistryClient client
         (getAllVersions subject)))))



(defn schema-version
  "Given a subject and a schema it returns the version of the schema"
  [{:keys [url client]} ^String subject ^Schema schema]
  (with-circuit-breaker url
    (return-nil-when-not-found
     (.. ^SchemaRegistryClient client
         (getVersion subject schema)))))



(defn schema-metadata
  "Returns the schema metadata for the given subject and version.
  If the version is not provided then it returns the latest version"
  ([{:keys [url client]} ^String subject]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (->clj
       (.. ^SchemaRegistryClient client
           (getLatestSchemaMetadata subject))))))
  ([{:keys [url client]} ^String subject ^long version]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (->clj
       (.. ^SchemaRegistryClient client
           (getSchemaMetadata subject version)))))))



(defn retrieve-schema
  "Given a schema id, and optionally a subject, it returns the Avro schema"
  ([{:keys [url client]} ^long id]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (.. ^SchemaRegistryClient client
          (getById id)))))
  ([{:keys [url client]} ^long id ^String subject]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (.. ^SchemaRegistryClient client
          (getBySubjectAndId subject id))))))



(defn subject-compatibility
  "Given a subject it returns the subject compatibility level or nil if not found.
   Without a subject it returns the default compatibility level"
  ([{:keys [url client] :as sr}]
   (subject-compatibility sr nil))
  ([{:keys [url client]} ^String subject]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (.. ^SchemaRegistryClient client
          (getCompatibility subject))))))



(defn update-subject-compatibility
  "Given a subject it updates the subject compatibility level to the given value
   Without a subject it updates the default compatibility level"
  ([{:keys [url client]} ^String level]
   (update-subject-compatibility url nil level))
  ([{:keys [url client]} ^String subject ^String level]
   (with-circuit-breaker url
     (return-nil-when-not-found
      (.. ^SchemaRegistryClient client
          (updateCompatibility subject level))))))



(defn register-schema
  "Given a subject and a schema it register the schema if new and returns the id"
  [{:keys [url client]} ^String subject ^Schema schema]
  (with-circuit-breaker url
    (.. ^SchemaRegistryClient client
        (register subject schema))))



(defn delete-subject
  "Given a subject it removes it if found and returns the list of
  deleted versions"
  [{:keys [url client]} ^String subject]
  (with-circuit-breaker url
    (return-nil-when-not-found
     (.. ^SchemaRegistryClient client
         (deleteSubject subject)))))



(defn delete-version
  "Given a subject and a schema version it removes it if found and
  returns the list of deleted versions"
  [{:keys [url client]} ^String subject ^long version]
  (with-circuit-breaker url
    (return-nil-when-not-found
     (.. ^SchemaRegistryClient client
         (deleteSchemaVersion subject (str version))))))



(defn parse-schema
  "Given a Avro schema as a string returns a Avro RecordSchema object"
  [^String schema]
  (Schema/parse schema))
