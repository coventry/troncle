(ns troncle.macroexpand-all
  (:require [troncle.macroshka :as m]
            [troncle.wrap :as w]
            [troncle.util :as u]
            [clojure.tools.trace :as t]
            :reload
            ))

(defn recur? [f] (or (and (coll? f) (= (first f) 'recur))
                     (-> f meta ::recur?)))

(defmacro macroexpand-macro
  [f result-tree]
  (let [ftree   (atom [])
        vftree  (m/assign-var "ftree" ftree)
        ;; compile-time context here is the target code, so we get any
        ;; effects there for free. E.g.
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
            (let [[[f' t] & r] @expansions
                  ;; Wrong ctx for (macroexpand f), so can't check = f'
                  _            (swap! expansions rest)
                  ;; Mark forms which contain a recur, because the tracing
                  ;; instrumentation can screw up tail position.
                  f'           (vary-meta f' assoc ::recur? (recur? f'))
                  rv           (reconstruct-expansion [f' t] wrapper)]
              (if wrapper (wrapper rv) rv)))))

(defn reconstruct-expansion [[form expansions :as ptree] & [wrapper]]
  (if (empty? @expansions)
    form
    ;; This runs w/walk-wrap on the same form as macroexpand-macro
    ;; did, pulling out the results from there and stitching them into
    ;; the form, relying on the fact that the call-order is the same.
    (let [replacement (replacement-fn expansions wrapper)
          rv          (w/walk-wrap replacement form)
          recur?      (or (some #(-> % meta ::recur?) rv))
          recur-site? (#{'loop* 'fn*} (first rv))
          ;; Can only screw up the tail position of a form containing
          ;; recur if form is still inside the recursion point, so
          ;; keep track of that
          recur-mark? (and recur? (not recur-site?))]
      (vary-meta rv update-in [::recur?] (constantly recur-mark?)))))

(defn get-expansion-info [f]
  (let [a (atom []), av (m/assign-var "tree" a)]
    (eval `(macroexpand-macro ~f ~av))
    (binding [*print-meta* true] (u/pprint @a))
    (first @a)))

(defn macroexpand-all [f]
  (-> (get-expansion-info f) reconstruct-expansion doall))

(defn wrap [f wrapper]
  (-> (get-expansion-info f) (reconstruct-expansion wrapper) doall))
