(ns troncle.macroshka
  "Inside-out macro: wrap evaluable forms at the top level of the
  current form in a macro which wraps the evaluable forms at the next
  level down."
  (:require [troncle.wrap :as w]))

;; Becase it's a recursing macro, wm/wrap-form needs to reference fns
;; as var symbols.
(defonce dummy-ns (create-ns (gensym))) ;; Keep the vars here
(defn assign-var 
  "Return a fully-qualified symbol of a var assigned to value v"
  [name v]
  (let [s (gensym name)]
    (intern dummy-ns s v)
    (symbol (-> dummy-ns ns-name str) (str s))))

(defn wrap-layer [wrapper f] (w/walk-wrap wrapper (macroexpand f)))

(defn wrap-form* 
  "When (stop? f) is true, the result of (->> f inner (outer f)) is
  returned.  Otherwise, return value is (->> f inner macroexpand
  (w/walk-wrap wrapper) outer). (w/walk-wrap wrapper) will wrap the
  evaluable top-level forms of the macroexpansion in further instances
  of wrap-form.  This will cause further recursion into this function
  upon macroexpansion."
  [stop? inner outer wrapper f]
  (let [ft (inner f), wft (if (stop? f) ft (wrap-layer wrapper ft))]
    (outer f wft)))

(defmacro wrap-form 
  "stop? and transform are ns-qualified symbols for functions.
  See wrap-form* for their meaning."
  [stop? inner outer f]
  (wrap-form* (find-var stop?)
              (find-var inner)
              (find-var outer)
              #(if (sequential? %)
                 (list `wrap-form stop? inner outer %)
                 %)
              f))

