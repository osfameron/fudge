;; # Minimal logging library

(ns fudge.log
  (:require [java-time :as dt]
            [cheshire.core :as json])
  (:gen-class))

;; # Utility functions for logging configs

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

;; # Log-levels

(defn set-valid-levels
  "Set `:valid-levels` for logging given

   - an ordered list of `:levels`
   - the minimum `:level`"
  [{level :level levels :levels :as config}]
  (assoc config
         :valid-levels
         (->> levels
              (drop-while (partial not= level))
              set)))

(defn check-level
  "Check that the `:level` in the log call is one of
   the accepted `:valid-levels`"
  [config data]
  (contains? (:valid-levels config) (:level data)))

;; # Normalizing logging data

(defn normalize-data
  "Ensure the `data` structure being logged is in hash-map form.
   If not, turn it into a hash with the original data under the
   `:message` key"
  [data]
  (if (map? data)
      data
      {:message data}))

(defn prepare-data-for-logging
  "Prepare the `data` structure being logged by setting the `:level`
   and `:date` values"
  [config data level]
  (->> data
       normalize-data
       (merge {:date (call config :date-fn)
               :level level})))

;; # The logger configuration
;;
;; ## Commonly overridden
;;
;; (These should be easy and safe to override, and will satisfy most logging
;; requirements)
;;
;; - `:level`      a keyword specifying minimum log-level (default :info)
;; - `:levels`     ordered list of log levels
;;                 (default [:trace :debug :info :warn :error :fatal :report])
;; - `:format-fn`  function to transform the log data
;;                 (default `identity`, eg. leave as Clojure data, but logging
;;                 configs are provided for common formats:
;;                 see plain-config, json-config, aws-log-config)
;; - `:output-fn`  function to output the log line.
;;                 (default: print to standard output)
;;
;; ## Deep customization
;;
;; (For more complex requirements.  Changing these may change the behaviour of logging.)
;;
;; - `:log?-fn`           whether to output log or not
;;                        (default: check if log-level provided matches minimum level)
;; - `:prepare-fn`        coerces the log data into a hash-map including
;;                        date and log-level
;; - `:date-fn`           function to retrieve date (used by :prepare-fn)
;; - `:setup-config-fn`   sets up log-level data structures
;;                        (could be specialised to do setup for output)

(def default-config
  "Base logger configuration. By default we do the following

   - accept a standard set of logging levels
   - log on `:info` or greater
   - add a date in a standard format
   - output as a Clojure data structure
   - to Standard Output only"
  {:level :info
   :levels [:trace :debug :info :warn :error :fatal :report]
   :format-fn identity
   :output-fn println
   ; but see http://yellerapp.com/posts/2014-12-11-14-race-condition-in-clojure-println.html

   :log?-fn check-level
   :setup-config-fn set-valid-levels
   :prepare-fn prepare-data-for-logging
   :date-fn (comp dt/format dt/zoned-date-time)})

;; # Create a logger

(defn get-logger
  "Return a logger from the supplied config hash(es)"
  [& config]
  (-> (apply merge default-config config)
      (invoke :setup-config-fn)))

;; # Main logging functions

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

;; # Common format configs

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
  (-> record
      ((juxt get(comp json/generate-string dissoc)) :date)
      ((partial clojure.string/join " "))))

(def aws-log-config
  "Config for AWS JSON Log event format"
  {:format-fn format-aws-log})
