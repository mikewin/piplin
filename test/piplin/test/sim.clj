(ns piplin.test.sim
  (:use [piplin sim modules math])
  (:use clojure.test))

(deftest what-changed-test
  (is (= (what-changed {:a 1 :b 2 :c 3 :d 4}
                       {:b 2 :c 3 :d 5})
         [{:a 1, :c 3, :b 2, :d 5}
          {:d 5}])
      "check that we identify everything that
      changed and merge properly"))

(deftest next-fns-test
  (is (= (next-fns 3 [:a :b :c]
                   {3 {:f1 []}
                    4 {:f2 []}
                    :b {:f3 [] :f5 []}
                    :d {:f4 [] :f6 []}})
         [{:f1 [] :f3 [] :f5 []}
          {4 {:f2 []} :d {:f4 [] :f6 []}}])
      "next functions and remaining functions
      correctly identified"))

(deftest run-cycle-test
  (let [[delta reactors] (run-cycle
                           2
                           {:a 1 :b 2}
                           {(fn [a b cycle]
                              [{:c (+ a b)}
                               {(inc cycle) [#(str "next")]
                                :b [#(str "baz")]}])
                            [:a :b :cycle]
                            (fn [a]
                              [{:a 22}
                               {:b [#(str "bar")]}])
                            [:a]})]
    (is (= delta {:c 3 :a 22})
        "make sure full delta is generated")
    (is (= ((first (get reactors 3))) "next")
        "get reactor for cycle event")
    (is (= (set (map #(%) (:b reactors)))
           (set ["bar" "baz"]))
        "combining reactors for same event"))
  (is (thrown? AssertionError
               (run-cycle
                 2
                 {:a 1 :b 2}
                 {(fn [a b cycle]
                    [{:c (+ a b)}
                     {(inc cycle) [#(str "next")]
                      :b [#(str "baz")]}])
                  [:a :b :cycle]
                  (fn [a]
                    [{:a 22 :c -1}
                     {:b [#(str "bar")]}])
                  [:a]}))))

(deftest exec-sim-test
  (let [[counterfn arglist] (every-cycle (fn [x] (inc x))
                             [:count]
                             :count)]
    (is (= (exec-sim {:count 0} {counterfn arglist} 10)
           {:count 10})))
  (let [mod (module [:outputs [c (instance (uintm 8) 0)]]
                        (connect c (+ c 1)))
        sim (make-sim mod)
        init-state (first sim)
        init-fns (ffirst (second sim))]
    (is (= (get (exec-sim init-state
                          (apply hash-map init-fns)
                          10)
                [(:token mod) :c])
           (instance (uintm 8) 10))
        "ran and counted up to 10")))
