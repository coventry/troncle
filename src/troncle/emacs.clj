(ns troncle.emacs
  (:require [troncle.core :as c]
            [troncle.traces :as traces]
            [troncle.discover :as discover]
            [clojure.tools.trace :as t]
            [nrepl.discover :as d]
            [clojure.tools.nrepl.misc :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface with emacs

(defmacro discover-ify
  "Add the nrepl-discover metadata to the var fn XXX incomplete"
  [fn args]
  (let [m (meta fn)]))

(defn safe-read [s]
  (binding [*read-eval* nil]
    (read-string s)))

(defn tracer
  "Instrument a form with tracing code, tracking its source code
  position."
  [line-offset form form-transformed]
  (let [[line column] ((juxt :line :column) (meta form))
        line (+ line line-offset)]
    (list 'clojure.tools.trace/trace
          (pr-str [line column] form) form-transformed)))

(defn trace-region
  "Eval source, taken from source-region instrumenting all forms
  contained in trace-region with tracing"
  [{:keys [transport source source-region trace-region ns] :as msg}]
  (let [source-region (safe-read source-region)
        trace-region (safe-read trace-region)
        soffset (nth source-region 1)
        loffset (->> source c/line-starts
                     (c/line-column-from-offset soffset) first)
        [tstart tend] (map #(- (nth trace-region %) soffset) [1 2])
        ns (-> ns symbol the-ns)]
    (c/trace-marked-forms source tstart tend ns
                           (partial tracer loffset))
    ;; Error handling is currently handled in
    ;; nrepl.discover/wrap-discover-logic.  
    (@traces/trace-execution-function)))

;; Add the nrepl-discover metadata
(let [m (meta #'trace-region)
      opmap {:nrepl/op {:name (-> m :name str) :doc (m :doc)
                        :args [["source"        "string"]
                               ["source-region" "region"]
                               ["trace-region" "region"]]}}]
  (alter-meta! #'trace-region conj opmap))

(defn set-trace-execution-function
  "Set the function which is called when forms are sent for
  compilation with tracing instrumentation."
  [{:keys [transport var] :as msg}]
  ())

(def provided true)
