;; Minimal logging library

(ns fudge.log
  (:require [java-time :as dt]
            [cheshire.core :as json])
  (:gen-class))

(defn call
  "Call a function indexed by the keyword k
   in a logging config c."
  [c k & args]
  (apply (k c) args))

(defn invoke
  "Invoke a function indexed by the keyword k
   in a logging config c, passing the config itself as
   the first parameter (similar to OO method invocation)."
  [c k & args]
  (apply (k c) c args))

(defn set-valid-levels
      "Set :valid-levels for logging given:
        - an ordered list of :levels
        - the minimum :level"
      [{level :level levels :levels :as config}]
      (assoc config
             :valid-levels
             (->> levels
                  (drop-while (partial not= level))
                  set)))

(defn check-level
     "Check that the :level in the log call is one of
      the accepted :valid-levels"
     [config data]
     (contains? (:valid-levels config) (:level data)))

(defn normalize-data
      "Ensure the `data` structure being logged is in hash-map form.
       If not, turn it into a hash with the original data under the
       :message key"
      [data]
      (if (map? data) data {:message data}))

(defn prepare-data-for-logging
    "Prepare the `data` structure being logged by setting the :level
     and :date values"
    [config data level]
    (->> data
         normalize-data
         (merge {:date (call config :date-fn)
                 :level level})))

(def default-config
  "Base logger configuration. By default we do the following
    - accept a standard set of logging levels
    - log on :info or greater
    - add a date in a standard format
    - output as a Clojure data structure
    - to Standard Output only
   We expect the user to request a more elaborate logger using
   the get-logger function."
  {:level :info
   :levels [:trace :debug :info :warn :error :fatal :report]
   :setup-config-fn set-valid-levels
   :prepare-fn prepare-data-for-logging
   :log?-fn check-level
   :date-fn (comp dt/format dt/zoned-date-time)
   :format-fn identity
   :output-fn println})
; but see http://yellerapp.com/posts/2014-12-11-14-race-condition-in-clojure-println.html

(defn get-logger
  "Return a logger from the supplied config hash(es)"
  [& config]
  (-> (apply merge default-config config)
      (invoke :setup-config-fn)))

(defn log
  "Log a record with the config, level, and data provided"
  [c level data]
  (let [record (invoke c :prepare-fn data level)]
    (when (invoke c :log?-fn record)
      (->> record
           (call c :format-fn)
           (call c :output-fn)))))

(defn spy-with
  "Log a record about a data value, first applying the transform
   supplied.  Returns the original, untransformed value.  Designed
   to be used in threaded pipelines.  For example:
      (-> {:counter 1}
          (update :counter inc)
          (spy-with #(str \"The number is now \" (:counter %)))
          (update :counter inc))"
  [c transform level data]
  (doto data
    (->> transform
         (log c level))))

(defn spy
  "Log a record about a data value as per `spy-with`, but with no transformation."
  [c level data]
  (spy-with c identity level data))

(defn format-plain
  "Simple plain string formatter"
  [{:keys [:date :level :message]}]
  (str date " [" level "] " message))

(def plain-config
  "Config for a plain text log message format"
  {:format-fn format-plain})

(def json-config
  "Config for a JSON serialized format"
  {:format-fn json/generate-string})

(defn format-aws-log
  "Format function for AWS Cloudwatch logs: a date, followed by a JSON string"
  [record]
  (let [[date record'] ((juxt get dissoc) record :date)]
    (str date " " (json/generate-string record'))))

(def aws-log-config
  "Config for AWS JSON Log event format"
  {:format-fn format-aws-log})
