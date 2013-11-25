(ns troncle.macroexpand-all
  (:require [troncle.macroshka :as m]
            [troncle.wrap :as w]
            [troncle.util :as u]
            [clojure.tools.trace :as t]
            :reload
            ))

(defmacro macroexpand-macro
  [f result-tree]
  (let [ftree   (m/assign-var "ftree" (atom []))
        me      (macroexpand f)
        _       (swap! @(find-var result-tree) conj [me @(find-var ftree)])
        wrapper (fn [g] (if (sequential? g)
                          `(macroexpand-macro ~g ~ftree) g))]
    (w/walk-wrap wrapper me)))

(defn macroexpand-all
  [f]
  (let [a (atom []), av (m/assign-var "tree" a)]
    (eval `(macroexpand-macro ~f ~av))
    (u/pprint a)
    ))

#_(-> '(let [->> (fn [& args] args)] (-> 1 inc))
    macroexpand-all)

(macroexpand-all
 '(inc (loop [i 10]
         (if (= i 0) 11 (recur (dec i))))))

