(ns troncle.emacs
  (:require [clojure.tools.trace :as t]
            [troncle.core :as c]
            [troncle.traces :as traces]
            [nrepl.discover :as d]
            [clojure.tools.nrepl.misc :as m]
            :reload))

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
        [tstart tend] (map #(- (nth trace-region %) soffset) [1 2])
        ns (-> ns symbol the-ns)]
    (try (c/trace-marked-forms source tstart tend ns
                               (partial tracer soffset))
         (@traces/trace-execution-function)
         (catch Throwable e
           (clojure.repl/pst e)))))

;; Add the nrepl-discover metadata
(let [m (meta #'trace-region)
      opmap {:nrepl/op {:name (-> m :name str) :doc (m :doc)
                        :args [["source"        "string"]
                               ["source-region" "region"]
                               ["trace-region" "region"]]}}]
  (alter-meta! #'trace-region conj opmap))


