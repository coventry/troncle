(ns troncle.traces
  (:require [troncle.util :as u]
            [clojure.tools.trace :as ctt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing state

(def report-trace-function
  "Atom containing function used to report a trace to repl output"
  (atom nil)) ;; Default is set below

(def trace-cond
  "Atom containing predicate used for checking when to report a trace"
  (atom (constantly true)))

(def trace-fn-cond
  "Atom containing predicate used for checking when to report of a
  call to a traced var."
  (atom (constantly true)))

(def trace-hook
  "Atom containing function which is run on every trace call"
  (atom (constantly nil)))

(def trace-log
  "List of trace reports"
  (agent []))

(defn empty-trace-log []
  (send trace-log (constantly [])))

(def trace-execution-function
  "Function which is run when the trace is requested"
  (atom (constantly nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Control of tracing state

(def trace-args
  "LINE, COLUMN: The position in the source file of FORM being traced.
VALUE: The result of evaluating form."
  '[line column form value]) ;; Needs to be changed in tracer fn too

(defn sr
  "Set the function which is called to report a trace."
  [fn]
  (swap! report-trace-function (constantly fn)))

(defn sh
  "Set the function which is called on every trace site"
  [fn]
  (swap! trace-hook (constantly fn)))

(defn sc
  "Set the predicate function telling troncle whether to report a
  trace."
  [fn]
  (swap! trace-cond (constantly fn)))

;; Add the argument signature required of the containing function to
;; the doc strings
(doseq [v [#'sr #'sh #'sc]
        :let [d (-> v meta :doc)
              d' (format (str "%s\n\nfn should take arglist "
                              "[{:keys %s}]\n%s")
                         d (str trace-args)
                         (-> #'trace-args meta :doc))]]
  (alter-meta! v #(assoc %1 :doc d')))

(defn sa
  "Set the predicate function for whether or not to output trace of a
  call to a var traced with trace-var.  Takes a list of the args
  passed to the traced function."
  [fn]
  (swap! trace-fn-cond (constantly fn)))


(defn st
  "Set the function which is executed when troncle-trace-region is
  called, which should be a function of no arguments."
  [fn]
  (swap! trace-execution-function (constantly fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing actions

(defn repl-report-trace
  "Report a trace to repl output"
  [{:keys [line column form value]}]
  ;; Need to build the string up and print it out atomically, or
  ;; display will be out of order.
  (println (format "L:%d C:%d %s\n=> %s" line column form (pr-str value))))

;; Taken from clojure.tools.trace.
(defn trace-var
  "If the specified Var holds an IFn and is not marked as a macro, its
  contents is replaced with a version wrapped in a tracing call;
  otherwise nothing happens. Can be undone with untrace-var.

  In the unary case, v should be a Var object or a symbol to be
  resolved in the current namespace.

  In the binary case, ns should be a namespace object or a symbol
  naming a namespace and s a symbol to be resolved in that namespace."
  ([ns s]
     (trace-var (ns-resolve ns s)))
  ([v]
     (let [^clojure.lang.Var v (if (var? v) v (resolve v))
           ns (.ns v)
           s  (.sym v)]
       (if (and (ifn? @v) (-> v meta :macro not))
         (let [f @v
               vname (symbol (str ns "/" s))]
           (doto v
             (alter-var-root #(fn tracing-wrapper [& args]
                                ;; The part altered from c.t.t.
                                (if (@trace-fn-cond args)
                                  (ctt/trace-fn-call vname % args)
                                  (apply % args))))
             (alter-meta! assoc :clojure.tools.trace/traced f)))))))

(defn ^{:skip-wiki true} untrace-var*
  "Reverses the effect of trace-var / trace-vars / trace-ns for the
  given Var, replacing the traced function with the original, untraced
  version. No-op for non-traced Vars.

  Argument types are the same as those for trace-var."
  ([ns s]
     (untrace-var* (ns-resolve ns s)))
  ([v]
     (let [^clojure.lang.Var v (if (var? v) v (resolve v))
           ns (.ns v)
           s  (.sym v)
           f  ((meta v) ::traced)]
       (when f
         (doto v
           (alter-var-root (constantly ((meta v) ::traced)))
           (alter-meta! dissoc ::traced))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing macros

(defn tracer
  "Instrument a form with tracing code, tracking its source code
  position."
  [line-offset form form-transformed]
  (let [[line column] ((juxt :line :column) (meta form))
        line (+ line line-offset)
        value  (gensym "value")
        args {:line line :column column :form (list 'quote form)
              :value value}]
    `(let [ ;; Evaluation happens once, here
           ~value ~form-transformed]
       (@trace-hook ~args)
       (when (@trace-cond ~args)
         (@report-trace-function ~args))
       ~value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Convenience namespace

(ns t)

(doseq [[n v] (ns-publics 'troncle.traces)]
  (eval `(def ~n ~v)))

(ns troncle.traces)
