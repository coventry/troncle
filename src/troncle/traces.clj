(ns troncle.traces
  (:require [clojure.tools.trace :as t]))

(defn trace-fn
  ([f] (trace-fn nil f))
  ([msg f]
     (fn [& args]
       ;; Probably want to change this.  Does fairly ugly things with
       ;; a nil msg.
       (t/trace-fn-call msg f args))))


