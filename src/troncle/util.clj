(ns troncle.util
  (:require [clojure.walk2 :as w]
            [clojure.tools.trace :as t]
            [clojure.pprint :as pp]))

(defn pprint [& args] (apply pp/pprint args))
(defn trace [& args] (apply t/trace args))
(def deftrace #'t/deftrace)

(defn metadata-walk [form]
  (w/walk metadata-walk
          #(list form (meta form) %)
          form))

;; Correction for the fact that clojure.walk drops metadata
(defn walk [inner outer form]
  (let [i (w/walkt form inner) fm (meta form)]
    (outer (if fm (with-meta i fm) i))))
(defn postwalk [f form] (walk (partial postwalk f) f form))

(defn starbreak []
  (println "************************************************************************"))
