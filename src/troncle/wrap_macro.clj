(ns troncle.wrap-macro
  "Inside-out macro: wrap evaluable forms at the top level of the
  current form in a macro which wraps the evaluable forms at the next
  level down."
  (:require [troncle.wrap :as w]))

(defn wrap-form* 
  "When (stop? f) is true, the result of (->> f inner (outer f)) is
  returned.  Otherwise, return value is (->> f inner macroexpand
  (w/walk-wrap wrapper) outer). (w/walk-wrap wrapper) will wrap the
  evaluable top-level forms of the macroexpansion in further instances
  of wrap-form.  This will cause further recursion into this function
  upon macroexpansion."
  [stop? inner outer wrapper f]
  (let [ft (inner f)]
    (outer f (if (stop? f)
               ft
               (w/walk-wrap wrapper (macroexpand ft))))))

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
