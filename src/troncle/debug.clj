(ns troncle.debug
  "Control of debugging contexts"  
  (:require [clojure.core.async :as as]
            [troncle.convenience-namespace :as conv]
            [troncle.util :as u]))

(def break-threads
  "Map of debugging threads"
  (atom {}))

(defn debug-setter [thread-id]
  "Prepare the arguments which are passed to the tracing
  instrumentation in traces/tracer"
  (fn [args env]
    (let [lb (keys env)
          ;; Taken from JOC debug macro
          envmap (zipmap (map (fn [sym] `(quote ~sym)) lb) lb)]
      (assoc args :envmap envmap :thread (list 'quote thread-id)))))

(defn make-breakpointers [thread-id]
  (let [bvf #(do (swap! break-threads update-in [thread-id :cond] conj %3) nil)
        bff #(do (swap! break-threads update-in [thread-id :form] conj %3) nil)
        bv  (conv/assign-macro "bv" bvf)
        bf  (conv/assign-macro "bf" bff)]
    (println (str "Break on a condition with '(t/bv form)' or '(t/" bv " form)'."))
    (println "Form is evaluated in local context with extra local bindings")
    (println "{troncle-value <last value> troncle-form <last form>}")
    (println (str "Break on a form with '(t/bf form)' or '(t/" bf " form)'"))))

(defn set-debug-environment []
  (let [thread-id (conv/t-gensym "debug-")]
    (make-breakpointers thread-id)
    [thread-id (debug-setter thread-id)]))

(defn contextual-eval [ctx expr]
  ;; Taken from JOC debug macro
  (eval `(let [~@(mapcat (fn [[k v]] [k `'~v]) ctx)] ~@expr)))

(defn debug-command [ctx cmd]
  (let [{:keys [thread envmap]} ctx]
    (condp = (first cmd)
      ;; Evaluate a form
      'e  (let [form (rest cmd)
                v (->> form (contextual-eval envmap))]
            (swap! break-threads update-in [thread :history] conj [form v])
            (println v) :eval)
      ;; Step to next instruction
      'n  (do (swap! break-threads assoc-in [thread :step] true)
              :end-repl)
      ;; Continue
      'c  (do (swap! break-threads update-in [thread] dissoc :step)
              :end-repl)
      (do (println "Unrecognized command") :error))))

(defn create-repl-interactor [c]
  (let [ri (fn [& args] (as/>!! c (drop 2 args)))
        cv (conv/assign-macro "d" ri)]
    (println (str "Communicate with break repl using '(t/d <cmd>)' or '(t/"
                  cv " <cmd>)'"))
    cv))

(defn break-repl [ctx]
  "The loop of the repl"
  (println "Breaking at" (:form ctx))
  (let [c (as/chan) cv (create-repl-interactor c)]
    (while (not= :end-repl
                 (->> c as/<!! (debug-command ctx))))
    (as/close! c)
    (println "Closing repl channel" cv)))

(defn form-break? [form forms]
  (if ((set forms) form)
     (str "Breaking on form " form)))

(defn cond-break? [form value envmap conds]
  (let [lb (assoc envmap 'troncle-form form 'troncle-value value)
        etest (partial contextual-eval lb)]
    (if-let [cond (->> conds (keep etest) first)]
      (str "Breaking on condition " cond " at form " form
           " value " value))))

(defn breakpoint? [form value envmap thread]
  (if-let [binfo (@break-threads thread)]
    (or (and (binfo :step) "")
        (form-break? form (binfo :form))
        (cond-break? form value envmap (binfo :cond)))))

(defn break [{:keys [form value envmap thread] :as ctx}]
  (when-let [msg (breakpoint? form value envmap thread)]
    (println msg)
    (break-repl ctx)))

