(ns troncle.emacs
  (:require [troncle.core :as c]
            [troncle.traces :as traces]
            [troncle.discover :as discover]
            [clojure.tools.trace :as t]
            [nrepl.discover :as d]
            [clojure.tools.nrepl.misc :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface with emacs

(defn safe-read [s]
  (binding [*read-eval* nil]
    (read-string s)))

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
                           (partial traces/tracer loffset))
    ;; Error handling is currently handled in
    ;; discover/wrap-discover-logic.  
    {:message (@traces/trace-execution-function)}))

(defn set-exec-var
  "Set the function which is called when forms are sent for
  compilation with tracing instrumentation."
  [{:keys [transport var] :as msg}]
  (if-let [v (-> var symbol resolve)]
    (do (traces/st v)
        {:message (str "Execution fn set to var " var)})
    {:message "No such var." :status #{:error :done}}))

;; Publish all the functions in here to the discover framework.
(doseq [[n v] (ns-publics *ns*)]
  (alter-meta! v assoc :nrepl/op {:name (str n)}))


