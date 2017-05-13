;; # Minimal logging library
;;
;; Principles:
;;
;; - https://12factor.net/logs
;;   - single appender, e.g. STDOUT is trivial.
;;  (Everything else can be built atop this if you think you need it
;;   (but you probably don't))
;;
;; - functions all the way down
;;
;; - just Clojure data-structures
;;
;; - trivial to create appenders
;;    (e.g. like Timbre docs claim it is)
;;
;; - play nicely with pipeline style (like timbre's spy)
;;
;; Secondary concerns:
;;
;; - compiling out hidden log-levels with macros
;; - tracing/line numbers and such
;; - dynamic reloading of config
;;
;; Explicitly NOT design concerns
;;
;; - multiple appenders (just provide a map as your function)
;; - Java logging (your appender can interface if needed)
;; - writing to files (>> /var/log/foo.log or add to function)
;; - logrotation (logrotate.d or add to appender function)
;; - filtering (grep - or custom valid-function
;; - complicated dispatch

(ns fudge.log
  (:require [java-time :as dt]
            [cheshire.core :as json])
  (:gen-class))

(defn call [i k & args] 
  (apply (k i) args))

(defn invoke [i k & args] 
  (apply (k i) i args))

(def default-config
  {:setup-config-fn
    (fn [config]
      (let [{level :level levels :levels} config]
        (assoc config
               :valid-levels
               (->> levels
                    (drop-while (partial not= level))
                    set))))
   :prepare-fn
    (fn [config level data]
      (let [data (if (map? data) data {:message data})
            log-meta {:date ((:date-fn config))
                      :level level}]
        (merge log-meta data)))   
   :log?-fn
    (fn [config data]
      (contains? (:valid-levels config) (:level data)))
   :date-fn (comp dt/format dt/zoned-date-time)
   :pre-format-fn #(update % :level name)
   :format-fn json/generate-string
   :output-fn println
   :level :info
   :levels [:trace :debug :info :warn :error :fatal :report]})
;; but see http://yellerapp.com/posts/2014-12-11-14-race-condition-in-clojure-println.html

(def aws-log-format
  {:format-fn (fn [record]
                (let [date (:date record)
                      record (dissoc record :date)]
                  (str date " " (json/generate-string record))))}) 

(def plain-format
  {:format-fn (fn [{:keys [date level message]}]
                (str date " [" level "] " message))})

(def json-format
   {:format-fn json/generate-string})

(defn get-logger [& config]
  (-> (apply merge default-config config)
      (invoke :setup-config-fn))) 

(defn log [c level data]
  (let [record (invoke c :prepare-fn level data)]
    (when (invoke c :log?-fn record)
      (->> record
           (call c :pre-format-fn)
           (call c :format-fn)
           (call c :output-fn)))))

(defn spy-with [c transform level data]
  (doto data
    (->> transform
         (log c level))))

(defn spy [c level & args]
  (apply spy-with c identity level args))

