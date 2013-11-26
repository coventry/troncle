(ns troncle.wrap.wrappers
   "The only reason this is here is so I don't have to forward-declare
   these functions for wrap/general-wrap-dispatch etc.  That causes
   problems.  http://dev.clojure.org/jira/browse/CLJ-1276"
   (:require [troncle.wrap.special-form-info
              :refer :all]))

(defn wrap-internals [wrapper form]
  (cond
   ;; Following deals sensibly with function calls, since
   ;; function symbol will not be wrapped
   (seq?    form) (map wrapper form)
   (map?    form) (into {} (map #(vec (map wrapper %)) form))
   (set?    form) (into #{} (map wrapper form))
   (vector? form) (into [] (map wrapper form))
   :else (throw (IllegalArgumentException.)
                "Don't know this sequence")))

(defn wrap-let [wrapper [invocation bindings & body]]
  `(~invocation
    ~(->> bindings
          (partition-all 2)
          (mapcat (fn [[k v]] [k (wrapper v)]))
          vec)
    ~@(map wrapper body)))

(defn wrap-method 
  "Wrap a method from a deftype*-style declaration"
  [wrapper [methodname args & body]]
  `(~ methodname ~args ~@(map wrapper body)))

(defn wrap-deftype* [wrapper
                     [invocation classname class fields
                      & remainder]]
  (let [kwargs (->> remainder
                    (partition 2)
                    (take-while (comp keyword? first))
                    (reduce into []))
        methods (drop (count kwargs) remainder)]
    `(~invocation ~classname ~class ~fields ~@(concat kwargs)
                  ~@(map (partial wrap-method wrapper) methods))))

(defn wrap-reify* [wrapper [invocation interfaces & methods]]
  `(~invocation ~interfaces
                ~@(map (partial wrap-method wrapper) methods)))

(defn wrap-ignore-elements 
  "Wrap everything but the first numignore elements of form"
  [wrapper form numignore]
  (let [notwrapped (+ 1 numignore)] ; Exclude special-form symbol
    (concat (take notwrapped form)
            (map wrapper (drop notwrapped form)))))

(defn wrap-dot [wrapper [_ hostexpr mem-or-meth & remainder]]
  `(. ~(wrapper hostexpr)
      ~(if (sequential? mem-or-meth)
         (if (symbol? (first mem-or-meth))
             ;; don't make an external wrap of a (method-symbol
             ;; args*), but walk into it.
             (map wrapper mem-or-meth)
             ;; Could get confusing if we wrap the first element of
             ;; mem-or-meth, and it is not a list.
             (throw (IllegalArgumentException.)
                    "Malformed member expression"))
         mem-or-meth)
      ~@(map wrapper remainder)))

(defn wrap-case*
  [wrapper
   [_ symb shift mask default casemap switch-type test-type skip-check]]
  `(case* ~symb ~shift ~mask ~(wrapper default)
          ;; casemap is a map from integers to forms.  case* expects
          ;; an actual map object here, though, so can't just wrap the
          ;; whole thing, and wouldn't want to anyway.
          ~(into {} (map (fn [[k [t v]]] [k [t (wrapper v)]]) casemap))
          ~switch-type ~test-type ~skip-check))

(defn wrap-fn* [wrapper form]
  (let [prelude (take-while (complement sequential?) form)
        sigs (drop (count prelude) form)
        sigs (if (vector? (first sigs)) (list sigs) sigs)
        wsigs (map (fn [[bindings & body]]
                     `(~bindings ~@(map wrapper body)))
                   sigs)]
    (concat prelude wsigs)))

(defn wrap-if [wrapper [_ & remainder]]
  `(~_ ~@(map wrapper remainder)))

(defn wrap-try [wrapper [_ & remainder]]
  `(~_ ~@(for [e remainder]
           (condp = (and (sequential? e) (first e))
             ;; (catch) and (finally) need to be top-level forms in (try)
             'catch (wrap-ignore-elements
                     wrapper e
                     (exclude-initial-elements 'catch))
             'finally (wrap-internals wrapper e)
             ;; Default: wrap all the other elements of the try list
             (wrapper e)))))

