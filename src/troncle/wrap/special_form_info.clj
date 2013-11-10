(ns troncle.wrap.special-form-info
  "The only reason this is here is because of the forward-declaration
  problems described in the wrappers.clj docstring.")

;; Lists of functions which can all be wrapped the same way
(def treat-as-function-call
  "special forms which can be wrapped as if they were functions (all
  elements of the list get evaluated.)"
  #{'monitor-enter 'recur 'do 'monitor-exit 'throw 'finally 'if 'def})

(def treat-as-let #{'let* 'letfn* 'loop*})

(def exclude-initial-elements
  "map from special forms to the number of initial elements in their
  lists which should not be wrapped."
  {'new 1, 'set! 1, 'catch 2})

(def do-not-wrap-constituents
  "Special forms whose elements should not be wrapped at all"
  #{'quote 'var 'clojure.core/import*})

