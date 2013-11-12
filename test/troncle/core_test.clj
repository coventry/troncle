(remove-ns 'troncle.core-test)

(ns troncle.core-test
  (:require [clojure.test :refer :all]
            [troncle.core :refer :all]
            [troncle.traces :as t]
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

(def test-form (parse-tree test-string))

(defn line-column-from-offset
  "Get the line/column position from offset into the string."
  ([offset linestarts]
     (line-column-from-offset offset linestarts 0))
  ([offset linestarts startidx]
     (let [[linenum soffset]
           (last (take-while #(<= (% 1) offset)
                             (map-indexed vector
                                          (drop startidx linestarts))))]
       [(+ startidx linenum 1) (- offset soffset -1)])))

(deftest line-column-calculation
  (testing "Line/column conversion works"
    (doseq [[offset ch] (map-indexed vector test-string)
            :when (not (#{\newline \return} ch))            
            :let [[line col] (line-column-from-offset offset pos)
                  sch (nth (nth split-lines (- line 1)) (- col 1))]]
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

(deftest find-contained-forms
  (testing "Can we find the forms between two offsets"
    (let [cf (first (mark-contained-forms pos 5 50 test-form))
          defn (first cf) capitalize (second cf)]
      (is ((meta capitalize) :troncle.core/wrap))
      (is (not ((meta defn) :troncle.core/wrap))))))

(user/starbreak)
(run-tests)

;; (defonce myns-sym (gensym))
(def myns-sym 'troncle.tst)
(def myns (create-ns myns-sym))
(binding [*ns* myns]
  (clojure.core/refer-clojure))
(ns-unmap myns 'capitalize)

(swap! troncle.traces/trace-execution-function
       (constantly #(println ((ns-resolve myns-sym 'capitalize) "foo"))))

(trace-marked-forms
 test-string 5 100 myns
 #(list 'clojure.tools.trace/trace
                           (pr-str ((juxt :line :column) (meta %1)) %1) %2))

(@troncle.traces/trace-execution-function)
