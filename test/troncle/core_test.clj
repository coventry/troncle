(ns troncle.core-test
  (:require [clojure.test :refer :all]
            [troncle.core :refer :all]
            [clojure.string :as s]))

(def test-string "(defn ^String capitalize
  [^CharSequence s]
  (let [s (.toString s)]
    (if (< (count s) 2)
      (.toUpperCase s)
      (str (.toUpperCase (subs s 0 1))
           (.toLowerCase (subs s 1))))))
")

(def split-lines (s/split-lines test-string))
(def pos (line-starts test-string))

(defn line-column-from-offset
  "Get the line/column position from offset into the string."
  ([offset linestarts]
     (line-column-from-offset offset linestarts 0))
  ([offset linestarts startidx]
     (let [[linenum soffset]
           (last (take-while #(<= (% 1) offset)
                             (map-indexed vector
                                          (drop startidx linestarts))))]
       [(+ startidx linenum) (- offset soffset)])))

(deftest line-column-calculation
  (testing "Line/column conversion works"
    (doseq [[offset ch] (map-indexed vector test-string)
            :when (not (#{\newline \return} ch))            
            :let [[line col] (line-column-from-offset offset pos)
                  sch (nth (nth split-lines line) col)]]
      (is (= ch sch)))))

(deftest line-starts-smoke-test
  (testing "Do the line starts match up with split-lines?"
    (let [lines (for [[start end]
                      (map vector (butlast pos) (drop 1 pos))]
            (s/trim-newline (subs test-string start end)))]
      (is (= lines split-lines))))
  (testing "Is computation of offsets correct"
    (doseq [offset (range (count test-string))
            :let [[line col] (line-column-from-offset offset pos)]]
      (is (= offset (offset-from-line-column line col pos))))))

(run-tests)

