(ns troncle.macroshka
  "Inside-out macro: wrap evaluable forms at the top level of the
  current form in a macro which wraps the evaluable forms at the next
  level down."
  (:require [troncle.wrap :as w]))

;; Becase it's a recursing macro, macroexpand-macro needs to reference
;; fns as var symbols.
(defonce dummy-ns (create-ns (gensym "macroshka-var-ns__")))
(defn assign-var 
  "Return a fully-qualified symbol of a var assigned to value v"
  [name v]
  (let [s (gensym name)]
    (intern dummy-ns s v)
    (symbol (-> dummy-ns ns-name str) (str s))))

(defmacro macroexpand-macro
  [f result-tree]
  (let [ftree   (atom [])
        vftree  (assign-var "ftree" ftree)
        ;; compile-time context here is the target code, so we get any
        ;; effects there for free. E.g.
        ;; (defmacro n [t] (println (macroexpand t) (-> 1 inc)))
        ;; (let [-> (fn [& args] args)] (n (-> 1 inc))) => (-> 1 inc) 2
        me      (macroexpand f)
        _       (swap! @(find-var result-tree) conj [me ftree])
        mwrapper (fn [g] (if (coll? g)
                           `(macroexpand-macro ~g ~vftree)
                           g))]
    (with-meta (w/walk-wrap mwrapper me) (meta f))))

(defn recur-form? [f] (and (coll? f) (= (first f) 'recur)))
(defn recur? [f] (-> f meta ::recur?))

(declare reconstruct-expansion)

(defn replacement-fn [expansions wrapper]
  "Used to pull out the macroexpansions stored away during the
  macroexpand-macro phase."
  (fn [f] (if (not (coll? f))
            f
            (let [[[f' t] & r] @expansions
                  ;; Wrong ctx for (macroexpand f), so can't check = f'
                  _            (swap! expansions rest)
                  rv           (reconstruct-expansion [f' t] wrapper)
                  wrv          (if wrapper (wrapper f rv) rv)]
              ;; Mark forms which contain a recur, because the tracing
              ;; instrumentation can screw up tail position.
              (vary-meta wrv assoc ::recur?
                         (or (recur? wrv) (recur-form? wrv)))))))

(defn reconstruct-expansion [[form expansions] & [wrapper]]
  (if (empty? @expansions)
    form
    ;; This runs w/walk-wrap on the same form as macroexpand-macro
    ;; did, pulling out the results from there and stitching them into
    ;; the form, relying on the fact that the call-order is the same.
    (let [replacement (replacement-fn expansions wrapper)
          rv          (w/walk-wrap replacement form)
          ;; Check whether this is recur, or recur in form's tail 
          recur??     (or (recur-form? form) (-> rv last recur?))
          recur-site? (#{'loop* 'fn*} (first rv))
          ;; Can only screw up the tail position of a form containing
          ;; recur if form is still inside the recursion point, so
          ;; keep track of that
          recur-mark? (and recur?? (not recur-site?))
          rv          (vary-meta rv assoc ::recur? recur-mark?)]
      (if (coll? rv) (doall rv) rv))))

(defn get-expansion-info [f & [ns]]
  (let [a (atom []), av (assign-var "tree" a)]
    (binding [*ns* (or ns *ns*)] (eval `(macroexpand-macro ~f ~av)))
    (first @a)))

(defn macroexpand-all [f]
  (-> (get-expansion-info f) reconstruct-expansion))

(defn wrap [f wrapper ns]
  (-> (get-expansion-info f ns) (reconstruct-expansion wrapper)))

