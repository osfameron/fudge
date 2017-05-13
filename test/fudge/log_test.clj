(ns fudge.log-test
  (:require [clojure.test :refer :all]
            [fudge.log :refer :all]))

(defn date-mocker []
  (let [counter (atom 0)]
    {:date-fn (fn [& args] (format "2017-05-12T18:%02d" (swap! counter inc)))}))

(defn simple-logger []
  (get-logger (date-mocker) plain-format))

(deftest test-string-logger
  (testing "single log output"
      (let [logger (simple-logger)
            result (with-out-str
                     (log logger :info "foo"))]
        (is (= "2017-05-12T18:01 [info] foo\n" result))))

  (testing "multiple lines output"
    (let [logger (get-logger (date-mocker) plain-format)
          result (with-out-str
                    (log logger :info "foo")
                    (log logger :error "bar"))]
      (is (= "2017-05-12T18:01 [info] foo\n2017-05-12T18:02 [error] bar\n" result))))

  (testing "pipeline"
    (let [logger (get-logger (date-mocker) plain-format)
          result (with-out-str
                   (->> 1
                        inc
                        (spy logger :info)
                        inc
                        (spy-with logger #(* 10 %) :info)))]
      (is (= "2017-05-12T18:01 [info] 2\n2017-05-12T18:02 [info] 30\n" result)))))
       
