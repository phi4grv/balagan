(ns clojurewerkz.balagan.core-test
  (:require [clojure.set :as cs])
  (:use clojure.test
        clojurewerkz.balagan.core))

(defn vec-contains?
  "Wether the vec contains certain val"
  [vec val]
  (not (nil? (some #(= val %) vec))))

(defmacro has-all-paths?
  [extracted paths]
  `(do
     (is (= (count ~extracted)
            (count ~paths))
         (format "Unmatched extracted paths: %s"
                 (cs/difference (set ~extracted) (set ~paths))))
     (doseq [path# ~paths]
       (is (vec-contains? ~extracted path#)
           (format "Vector `%s` doesn't contain path `%s`" (vec ~extracted) path#)))))

(deftest extract-paths-test
  (testing "Extract paths from a vector"
    (has-all-paths? (extract-paths [1 2 3])
                    [[] [0] [1] [2]]))

  (testing "Extract paths from a simple map"
    (has-all-paths? (extract-paths {:a {:b {:c :d}}})
                    [[]
                     [:a]
                     [:a :b]
                     [:a :b :c]]))

  (testing "Extract paths from a map that has vectors"
    (has-all-paths? (extract-paths {:a :b :c {:d :e :f {:g '(1 2 3)}}})
                    [[]
                     [:a]
                     [:c]
                     [:c :d]
                     [:c :f]
                     [:c :f :g]
                     [:c :f :g 0]
                     [:c :f :g 1]
                     [:c :f :g 2]])))

(deftest path-matches?-test
  (is (path-matches? [0 1 2] [0 1 2]))
  (is (path-matches? [0 :* 2] [0 1 2]))
  (is (path-matches? [:c :f :g :*] [:c :f :g 0]))
  (is (path-matches? [0 odd? 2] [0 1 2])))

(deftest transform-test
  (let [res (update
             {:a :b :c {:d :e :f {:g [1 2 3]}}}
             [:c :f :g :*] inc)]
    (is (= {:a :b, :c {:f {:g [2 3 4]}, :d :e}})
        res)))

(deftest add-field-test
  (let [res (update
             {:a :b}
             [] (do->
                 (add-field :c :d)
                 (add-field :e :f)))]
    (is (= {:e :f :c :d :a :b}
           res))))

(deftest remove-field-test
  (let [res (update
             {:e :f :c :d :a :b}
             [] (do->
                 (remove-field :e)
                 (remove-field :c)))]
    (is (= {:a :b}
           res))))

(deftest fn-test
  (let [m {:a :1}
        res (update m [] #(assoc % :b 2))]
    (is (= 2 (:b res)))))

(deftest add-node-test
  (let [m {:a 1}
        res (update m
                       (new-path [:inc-a]) #(inc (:a %)))]
    (is (= 2
           (:inc-a res))))

  (let [m {:a :1}
        res (update m
                       (new-path [:b]) 2)]
    (is (= 2
           (:b res)))))


(deftest select-test
  (select {:a :b :c {:d :e :f {:g [1 2 3]}}}
          [:a] #(do
                  (is (= :b %1))
                  (is (= [:a] %2))))


  (select {:a {:b {:c 1} :d {:c 2}}}
          [:a :* :c] (fn [val path]
                       (if (= path [:a :b :c])
                         (is (= val 1))
                         (is (= val 2)))))

  (select {:a {1 {:c 1} 2 {:c 2} 3 {:c 3}}}
          [:a odd? :c] (fn [val path]
                         (if (= path [:a 1 :c])
                           (is (= val 1))
                           (is (= val 3)))))

  (select {:a [{:c 1} {:c 2} {:c 3}]}
          [:a even? :c] (fn [val path]
                          (if (= path [:a 0 :c])
                            (is (= val 1))
                            (is (= val 3))))))


(deftest transform-lazy-test
  (is (= {:a {:b [3 4 5]}}
         (update {:a {:b (map inc [1 2 3])}}
                    [:a :b :*] inc))))


(deftest transform-vector-test
  (is (= [2 3 4]
         (update [1 2 3]
                    [:*] inc)))
  (is (= [3 4 5]
         (update (map inc [1 2 3])
                    [:*] inc)))
  (is (= [{:a 2} {:a 3} {:a 4}]
         (update [{:a 1} {:a 2} {:a 3}]
                    [:* :a] inc))))

(deftest transform-dynamic-paths-test
  (let [res (update
             {:a {:b {}}}
             (new-path [:a :b :c]) (constantly {:d 1})
             [:a :b :c :d] inc)]
    (is (= {:a {:b {:c {:d 2}}}}
           res))))


(deftest transform-empty-list
  (let [res (update [{:a {:b '()}}
                        {:a {:b (map (constantly 1) [1])}}
                        {:a 1}]
                       [:* :a :b :*] inc)]
    (is (= [{:a {:b '()}} {:a {:b [2]}} {:a 1}]
           res))))