(ns troncle.wrap
  "Version for a wrapping macro.  Only targets top-level forms."
  (:use [clojure.pprint :only (pprint)])
  (:require [troncle.wrap.wrappers :refer :all]
            [troncle.wrap.special-form-info :refer :all]
            [clojure.set :as set])
)

(def general-wrap-dispatch
  "Special forms which need special logic"
  {'case* wrap-case*, 'fn* wrap-fn*, '. wrap-dot, 'try wrap-try,
   'deftype* wrap-deftype*, 'reify* wrap-reify*})

(defn walk-wrap [wrapper form]
  (if (not (sequential? form))
    form ;; Can't walk into it, so not very interesting to trace
    (let [invocation (first form)
          wrapped-internals
          (cond
           (treat-as-let             invocation)
             (wrap-let wrapper form)
           (exclude-initial-elements invocation)
             (wrap-ignore-elements
              wrapper form (exclude-initial-elements invocation))
           (do-not-wrap-constituents invocation)
             form
             (general-wrap-dispatch    invocation)
             ((general-wrap-dispatch invocation) wrapper form)
           :else
           ;; At this stage if it starts with a special form, it's one we can
           ;; wrap as we would a function call.
           (wrap-internals wrapper form))]
      wrapped-internals)))

