(ns troncle.traces
  (:require [clojure.tools.trace :as t]
            [troncle.core :as c]
            [nrepl.discover :as d]
            [clojure.tools.nrepl.misc :as m]
            :reload))

;; Bash clojure.tools.trace/tracer so that it can be bound dynamically
(let [m (-> #'t/tracer meta (assoc :dynamic true) (dissoc :private))]
  (alter-meta! #'t/tracer (constantly m)))

(defmacro with-tracer [tracer & forms]
  `(binding [t/tracer ~tracer] ~@forms))

(defn trace-fn
  ([f] (trace-fn nil f))
  ([msg f]
     (fn [& args]
       ;; Probably want to change this.  Does fairly ugly things with
       ;; a nil msg.
       (t/trace-fn-call msg f args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing state

(def trace-cond
  "Predicate used for checking when to report a trace"
  (atom (constantly true)))

(def trace-hook
  "Function which is run on every trace call"
  (atom (constantly nil)))

(def trace-log
  "List of trace reports"
  (agent []))

(defn empty-trace-log []
  (send trace-log (constantly [])))

(def trace-execution-function
  "Function which is run when the trace is requested"
  (atom (constantly nil)))


