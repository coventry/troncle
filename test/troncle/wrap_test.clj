(ns troncle.wrap-test
  (:use [clojure.pprint :only (pprint)])
  (:require [troncle.wrap :refer :all]
            [troncle.wrap.special-form-info :refer :all]
            [troncle.wrap.wrappers :refer :all]
            [troncle.util :as u]
            [clojure.tools.trace :as trace]
            [clojure.set :as set]
            [clojure.test :refer :all]))

(def special-forms
  "Convenience for making sure I've covered everything"
  (->> clojure.lang.Compiler/specials seq (map (comp symbol first)) set))

(def covered (set/union treat-as-function-call treat-as-let
                        (keys exclude-initial-elements)
                        do-not-wrap-constituents
                        (keys general-wrap-dispatch)))

(assert (= (set/union covered #{'&}) special-forms)
        [(set/difference covered special-forms)
         (set/difference special-forms covered)])

(defn wrapper [form] (if (coll? form) `(~'w ~form) form))
(def w identity)

(defmacro wrapper=
  ([input expected] `(wrapper= ~input ~expected nil))
  ([input expected msg]
     `(let [result# (walk-wrap wrapper ~input)
            ;; _# (eval result#)
            ] 
        (is (= result# ~expected) ~msg)))) 

(deftest function-wrapping
  (wrapper= '(inc    (dec 0))
            '(inc (w (dec 0)))
            "Wrapping should walk into function calls")
  (wrapper= '(   (comp inc)  0)
            '((w (comp inc)) 0)
            "Functions invocations returned by functions should be
            wrapped"))

(deftest object-wrapping
  ;; Regression test
  (wrapper= '[0 1] '[0 1]))

(deftest let-wrapping
  (wrapper= '(let* [a    (inc 0)  e 1]    (dec e))
            '(let* [a (w (inc 0)) e 1] (w (dec e)))
            "Wrapping captures let*'s binding expressions"))

(deftest deftype*-wrapping
  (let [head '(deftype*
                s
                debugger_playground.wrap2_test.s
                [g]
                :implements
                [debugger_playground.wrap2_test.t clojure.lang.IType])]
    (wrapper= `(~@head ~'(f [g h]    (inc g))  ~'(k [l]    (l (m n))     (l 1)))
              `(~@head ~'(f [g h] (w (inc g))) ~'(k [l] (w (l (m n))) (w (l 1))))
            "deftype* methods should be wrapped")))

(deftest throw-wrapping
  ;; Was getting the wrong result for this, because I thought the
  ;; throw argument should not be wrapped.  Regression test.
  (let [texpr   '(throw    (new java.lang.IllegalArgumentException    (str "No")))
        result (walk-wrap wrapper texpr)
        _ (is = '(throw (w (new java.lang.IllegalArgumentException (w (str "No"))))))]
    (is (thrown? java.lang.IllegalArgumentException (eval result)))))

(deftest ignore-elements
  (is (=
       (wrap-ignore-elements
        wrapper  ;; This is not a sane thing to do with deftype*.
        '(deftype* a b [c d] :implements [e]    (f [g h] (i j))     (k [l] (m) (n)))
        5)
        '(deftype* a b [c d] :implements [e] (w (f [g h] (i j))) (w (k [l] (m) (n)))))))

(deftest dot-wrapping
  (wrapper= '(. "." length) '(. "." length)
            "(. instance-expr member-symbol) Same form for (. Classname-symbol member-symbol)")
  (wrapper= '(. "foo" (charAt    (inc 0)))
            '(. "foo" (charAt (w (inc 0)))))
  (wrapper= '(.    (str "foo" "bar") length)
            '(. (w (str "foo" "bar")) length))
  (wrapper= '(. "foo" charAt    (inc 0))
            '(. "foo" charAt (w (inc 0)))))

(deftest case*-wrapping
  (wrapper= '(case* a 1 1    (inc a)  {0 [1    (count mystr)],  1 [Object 0]} :compact :hash-equiv nil)
            '(case* a 1 1 (w (inc a)) {0 [1 (w (count mystr))], 1 [Object 0]} :compact :hash-equiv nil)))

(deftest fn*-wrapping
  (wrapper= '(fn* a ([b c] b    (a (inc c)))   ([]    (a 1)))
            '(fn* a ([b c] b (w (a (inc c)))) ([] (w  (a 1)))))
  (wrapper= '(fn* ([b c] b    (dec (inc c)))  ([]     (dec 1)))
            '(fn* ([b c] b (w (dec (inc c)))) ([] (w  (dec 1)))))
  (wrapper= '(fn* cons  [x seq]    (. clojure.lang.RT (cons x seq)))
            '(fn* cons ([x seq] (w (. clojure.lang.RT (cons x seq))))),
            "Sane response to single-signature case?"))


(deftest try-wrapping
  (wrapper= '(try   (apply f args)  (finally    (pop-thread-bindings)))
            '(try (w (apply f args)) (finally (w (pop-thread-bindings))))))

(deftest lead-fn-call-wrapping
  (wrapper= '(   (constantly nil)     (inc 1))
            '((w (constantly nil)) (w (inc 1))),
            "does it know to wrap the lead form if it's not a symbol?"))

(deftest loop-wrapping
  (wrapper= '(loop [i    (inc 10)]     (inc i) (recur (dec i)))
            '(loop [i (w (inc 10))] (w (inc i) (recur (w (dec i)))))))

(deftest if-wrapping
  (wrapper= '(if    (= i 0) 11  (recur    (dec i)))
            '(if (w (= i 0)) 11 (recur (w (dec i))))))

(deftest map-wrapping
  (wrapper= '(dissoc    {(inc 1) (dec 2)}  2)
            '(dissoc (w {(inc 1) (dec 2)}) 2)))
