(ns troncle.traces
  (:require [clojure.tools.trace :as t]))

(defn trace-fn
  ([f] (trace-fn "" f))
  ([msg f]
     (fn [& args]
       (t/trace (str msg " input:") args)
       (t/trace (str msg " output:") (apply f args)))))


