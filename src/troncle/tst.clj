(ns troncle.tst
  (:use [clojure.test]))

(defn ^String capitalize
  [^CharSequence s]
  (let [s (.toString s)]
    (if (< (count s) 2)
      (.toUpperCase s)
      (str (.toUpperCase (subs s 0 1))
           (.toLowerCase (subs s 1))))))

(deftest capitalization
  (is (= "Foo" (capitalize "foo"))))
