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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface with emacs

(defmacro discover-ify
  "Add the nrepl-discover metadata to the var fn XXX incomplete"
  [fn args]
  (let [m (meta fn)]))

(defn trace-region
  "Eval source, taken from source-region instrumenting all forms
  contained in trace-region with tracing"
  [{:keys [transport source source-region trace-region ns] :as msg}]
  (let [soffset (source-region 1)
        [tstart tend] (map #(- (trace-region %) soffset [1 2]))
        tracer #(list 'clojure.tools.trace/trace
                      (pr-str ((juxt :line :column) (meta %1)) %1) %2)]
    (try (c/trace-marked-forms source tstart tend ns tracer)
         (catch Throwable e
           (clojure.repl/pst e)))))

(let [m (meta #'trace-region )
      opmap {:nrepl/op {:name (m :name) :doc (m :doc)
                        :args [["source"        "string"]
                               ["source-region" "region"]
                               ["trace-region"  "region"]]}}]
  (alter-meta! #'trace-region conj opmap))
