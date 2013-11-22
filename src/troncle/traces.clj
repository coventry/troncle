(ns troncle.traces
  (:require [troncle.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing state

(def report-trace-function
  "Atom containing function used to report a trace to repl output"
  (atom repl-report-trace))

(def trace-cond
  "Atom containing predicate used for checking when to report a trace"
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
