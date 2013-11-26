(ns troncle.macroexpand-all
  (:require [troncle.macroshka :as m]
            [troncle.wrap :as w]
            [troncle.util :as u]
            [clojure.tools.trace :as t]
            :reload
            ))

(defmacro macroexpand-macro
  [f result-tree]
  (let [ftree   (atom [])
        vftree  (m/assign-var "ftree" ftree)
        ;; This macroexpansion happens in the compile-time context of
        ;; the target code, so we get any effects there for free. E.g.
        ;; (defmacro n [t] (println (macroexpand t) (-> 1 inc)))
        ;; (let [-> (fn [& args] args)] (n (-> 1 inc))) => (-> 1 inc) 2
        me      (macroexpand f)
        _       (swap! @(find-var result-tree) conj [me ftree])
        wrapper (fn [g] (if (coll? g)
                          `(macroexpand-macro ~g ~vftree)
                          g))]
    (w/walk-wrap wrapper me)))

(defn replacement-fn [expansions wrapper]
  "Used to pull out the macroexpansions stored away during the
  macroexpand-macro phase."
  (fn [f] (if (not (coll? f))
            f
            ;; (assert (= f' (macroexpand f))) would be natural here,
            ;; but the whole point of this adventure is that we can't
            ;; blithely do a macroexpand without the local bindings
            ;; context.
            (let [[[f' t] & r] @expansions
                  recur?       (and (list? f')
                                    (= (first f') 'recur))]
              (swap! expansions rest)
              ;; We want to mark forms which contain a recur, because
              ;; the tracing instrumentation can screw up the tail
              ;; position.
              (vary-meta (reconstruct-expansion [f' t] wrapper)
                         update-in [::recur?] #(or % recur?))))))

(defn reconstruct-expansion [[form expansions :as ptree] & [wrapper]]
  (if (empty? @expansions)
    form
    ;; This runs w/walk-wrap on the same form as macroexpand-macro
    ;; did, pulling out the results from there and stitching them into
    ;; the form.
    (let [replacement (replacement-fn expansions wrapper)
          rv          (w/walk-wrap replacement (macroexpand form))
          recur?      (some #(-> % meta ::recur?) rv)
          recur-site? (#{'loop* 'fn*} (first rv))
          ;; We can only screw up the tail position of a form containing
          ;; recur if we're still inside the recursion point, so keep
          ;; track of that
          rv          (vary-meta rv update-in
                                 [::recur?] (constantly (and recur? (not recur-site?))))]
      (if wrapper (wrapper rv) rv))))

(defn get-expansion-info [f]
  (let [a (atom []), av (m/assign-var "tree" a)]
    (eval `(macroexpand-macro ~f ~av))
    (first @a)))

(defn macroexpand-all [f]
  (-> (get-expansion-info f) reconstruct-expansion doall))

(defn wrap [f wrapper]
  (-> (get-expansion-info f) (reconstruct-expansion wrapper) doall))

(user/starbreak)

(-> '(let [-> (fn [& args] args)] (-> 1 inc))
    macroexpand-all doall u/pprint)

(def eg '(inc (loop [i 10]
            (if (= i 0) 11 (recur (dec i))))))

(-> eg (wrap  #(if (not (-> % meta ::recur?)) (list 'do % '(println "here")) %)))

