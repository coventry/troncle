(ns troncle.tst
  (:use [clojure.test])
  (:require [clojure.zip :as zip]
            [troncle.macroshka :as wm]))

(defn ^String capitalize
  [^CharSequence s]
  (let [s (.toString s)]
    (if (< (count s) 2)
      (.toUpperCase s)
      (.toLowerCase
       (str (.toUpperCase (subs s 0 1))
            (.toLowerCase (subs s 1)))))))

(deftest capitalization
  (is (= "Foo" (capitalize "foo"))))

(def data '[[a * b] + [c * d]])
(def dz (zip/vector-zip data))

(defn zip-eg []
   ;;'replace' * with /
   (loop [loc dz]
     (if (zip/end? loc)
       (zip/root loc)
       (recur
        (zip/next
         (if (= (zip/node loc) '*)
           (zip/replace loc '/)
           loc))))))

(defn call-zip-eg [] (zip-eg))

