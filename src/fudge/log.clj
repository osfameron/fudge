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
      (when (contains? (:valid-levels config) (:level data))
            data))
   :date-fn (comp dt/format dt/zoned-date-time)
   :format-fn json/generate-string
   :output-fn println
   :level :info
   :levels [:trace :debug :info :warn :error :fatal]})
;; but see http://yellerapp.com/posts/2014-12-11-14-race-condition-in-clojure-println.html

(defn make-logging-fn [config]
  (let [c (->> config
               (merge default-config)
               (#( (:setup-config-fn %) %)))
               ;; ... but why doesn't following work?
               ;; ((juxt :setup-config-fn identity))
               ;; seq
               ;; eval)
               ;; No matching ctor found for class clojure.core$comp$fn__4727
        {:keys [:prepare-fn :format-fn :output-fn :log?-fn]} c]
    (fn [level data]
        (some->> data
                 (prepare-fn c level)
                 (log?-fn c)
                 format-fn
                 output-fn))))

(def log (make-logging-fn {}))

(defn spy-with [logger transform level data]
  (doto data
    (->> transform
         (logger level))))

(defn spy [logger & args]
  (apply spy-with logger identity args))

(comment
  (log :info "hello there")

  ;; pipeline example
  (->> 1
       inc
       (spy log :info)
       inc
       (spy-with log (fn [n] {:number n}) :info)))
       
