(ns fudge.log-test
  (:require [clojure.test :refer :all]
            [fudge.log :refer :all]))

(deftest test-call
  (is (= 2 (call {:fn inc} :fn 1))))

(deftest test-invoke
  (is (= 2 (invoke
            {:fn (fn [c & args] (apply + (:val c) args))
             :val 1}
            :fn
            1))))

(deftest test-valid-levels
  (is (= #{:info :warn :error}
         (-> {:level :info
              :levels [:trace :debug :info :warn :error]}
             (set-valid-levels)
             :valid-levels))))

(deftest test-check-level
  (let [config {:valid-levels #{:info :warn :error}}]
    (is (check-level config {:level :info}))
    (is (check-level config {:level :error}))
    (is (not (check-level config {:level :debug})))))

(deftest test-normalize-data
  (testing "Coerced to map"
    (is (= {:message "Hello World"} (normalize-data "Hello World")))
    (is (= {:message [1 2 3]} (normalize-data [1 2 3]))))
  (testing "Map passed in"
    (is (= {:message "Hello World"} (normalize-data {:message "Hello World"})))))

(deftest test-prepare-data-for-logging
  (let [config {:date-fn (constantly "2017-07-08")}
        expected {:message "Hello World",
                  :level :info
                  :date "2017-07-08"}]
    (is (= expected (prepare-data-for-logging config "Hello World" :info)))
    (is (= expected (prepare-data-for-logging config {:message "Hello World"} :info)))))

(deftest test-get-logger
  (let [logger (get-logger {:setup-config-fn #(assoc % :foo :foo)}
                           {:level :error})]
    (testing "Config merged in literal value"
      (is (= :error (:level logger))))
    (testing "Config ran :setup-config-fn"
      (is (= :foo (:foo logger))))
    (testing "Config retained values from base config that weren't overridden"
      (is (= identity (:format-fn logger))))))

(deftest test-log
  (testing "Log"
    (let [logger {:prepare-fn (fn [c d l] (assoc d :level l))
                  :log?-fn (constantly true)
                  :format-fn (juxt :level :message)
                  :output-fn (partial clojure.string/join " ")}]
      (is (= ":info MSG" (log logger :info {:message "MSG"})))))
  (testing "Don't log")
    (let [logger {:prepare-fn (constantly {})
                  :log?-fn (constantly false)}]
      (is (= nil (log logger :info {:message "MSG"})))))

(deftest test-spy-and-spy-with
  (let [out (atom "")
        logger {:prepare-fn (fn [c  d l] d)
                :log?-fn (constantly true)
                :format-fn identity
                :output-fn (partial reset! out)}]
    (testing "spy-with"
      (let [result (spy-with logger count :info "Hello")]
        (is (= "Hello" result))
        (is (= 5 @out))))
    (testing "spy"
      (let [result (spy logger :info "Hello")]
        (is (= "Hello" result))
        (is (= "Hello" @out))))))

(deftest test-formats
  (let [record {:date "2017-07-08"
                :level :info
                :message "Hello"}]
    (testing "format-plain"
      (is (= "2017-07-08 [:info] Hello"
             (format-plain record))))
    (testing "format-aws-log"
      (is (= "2017-07-08 {\"level\":\"info\",\"message\":\"Hello\"}"
             (format-aws-log record))))))

;; End to end tests

(defn date-mocker []
  (let [counter (atom 0)]
    {:date-fn (fn [& args] (format "2017-05-12T18:%02d" (swap! counter inc)))}))

(defn plain-logger []
  (get-logger (date-mocker) plain-config))

(defn json-logger []
  (get-logger (date-mocker) json-config))

(defn aws-logger []
  (get-logger (date-mocker) aws-log-config))

(deftest test-plain-logger
  (testing "single log output"
      (let [logger (plain-logger)
            result (with-out-str
                     (log logger :info "foo"))]
        (is (= "2017-05-12T18:01 [:info] foo\n" result))))

  (testing "multiple lines output"
    (let [logger (plain-logger)
          result (with-out-str
                    (log logger :info "foo")
                    (log logger :error "bar"))]
      (is (= "2017-05-12T18:01 [:info] foo\n2017-05-12T18:02 [:error] bar\n" result))))

  (testing "pipeline"
    (let [logger (plain-logger)
          result (with-out-str
                   (->> 1
                        inc
                        (spy logger :info)
                        inc
                        (spy-with logger #(* 10 %) :info)))]
      (is (= "2017-05-12T18:01 [:info] 2\n2017-05-12T18:02 [:info] 30\n" result)))))

(deftest test-json-logger
  (let [logger (json-logger)
        result (with-out-str
                 (log logger :info "foo"))]
    (is (= "{\"date\":\"2017-05-12T18:01\",\"level\":\"info\",\"message\":\"foo\"}\n" result))))

(deftest test-aws-logger
  (let [logger (aws-logger)
        result (with-out-str
                 (log logger :info "foo"))]
    (is (= "2017-05-12T18:01 {\"level\":\"info\",\"message\":\"foo\"}\n" result))))
