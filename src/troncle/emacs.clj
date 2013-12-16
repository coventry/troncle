(ns troncle.emacs
  (:require [troncle.core :as c]
            [troncle.traces :as traces]
            [clojure.tools.trace :as ctt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface with emacs

(defn get-troncle-source
  "Called when troncle.el is loaded, to get the elisp interaction
  logic"
  [& dummy] ;; Not meant to be called via discovery mechanism
  (slurp (clojure.java.io/resource "troncle-core.el")))

(defn safe-read [s]
  (binding [*read-eval* nil]
    (read-string s)))

(defn instrument-form
  "Eval source, taken from source-region instrumenting all forms
  contained in trace-region with tracing"
  [{:keys [transport source source-region trace-region ns] :as msg}
   & instrumentation-args]
  (let [source-region (safe-read source-region)
        trace-region (safe-read trace-region)
        soffset (nth source-region 1)
        loffset (->> source c/line-starts
                     (c/line-column-from-offset soffset) first)
        [tstart tend] (map #(- (nth trace-region %) soffset) [1 2])
        ns (-> ns symbol the-ns)
        wrapper (partial traces/tracer loffset instrumentation-args
                         'troncle.traces/trace-hooks)]
    (c/trace-marked-forms source tstart tend ns wrapper)))

(defn trace-region [msg]
  (instrument-form msg)
  ;; discover/wrap-discover-logic does error handling
  {:message (pr-str (@traces/trace-execution-function)) :status :done})


(defn set-exec-var
  "Set the function which is called when forms are sent for
  compilation with tracing instrumentation."
  [{:keys [transport var] :as msg}]
  (if-let [v (-> var symbol resolve)]
    (do (traces/st v)
        {:message (str "Execution fn set to var " var)})
    {:message "No such var." :status #{:error :done}}))

;; Taken from nrepl.discover
(defn toggle-trace-var
  "Wrap or unwrap calls to the given var in tracing instrumentation"
  [{:keys [transport var] :as msg}]
  (try
    (if-let [v (resolve (symbol var))]
      (if (-> v meta :clojure.tools.trace/traced)
        (do (ctt/untrace-var* v)
            {:status :done :message (str var " untraced.")})
        (do (traces/trace-var v) ;; Altered from nrepl.discover
            {:status :done :message (str var " traced.")}))
      {:status #{:error :done} :message "no such var"})
    (catch Exception e
      (#'ctt/tracer "Error" (.getMessage e))
      {:status #{:error :done} :message (.getMessage e)})))

;; Publish all the functions in here to the discover framework.
(doseq [[n v] (ns-publics *ns*)]
  (alter-meta! v assoc :nrepl/op {:name (str n)}))
