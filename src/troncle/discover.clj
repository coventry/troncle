(ns troncle.discover
  "Plugs into nrepl middleware.  When an op of name N is sent to
  nrepl, e.g. by nrepl.el's nrepl-send-op, the message M is passed to
  wrap-discover.  It searches for a var V with metadata {:nrepl/op
  {:name N}}, and calls (V M).

  Taken from technomancy's nrepl-discover"
  (:require [clojure.tools.nrepl.transport :as t]
            [clojure.tools.nrepl.misc :as m]
            [clojure.tools.nrepl.middleware.session :as ses]
            [troncle.emacs]
            [clojure.repl]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; nrepl interface

(declare discover)

(defn ops []
  "Search all vars for metadata with key {:nrepl/op}.  Return a
  dictionary of them keyed by their (:name)s."
  (into {"discover" #'discover}
        (for [n (all-ns)
              [_ v] (ns-publics n)
              :when (:nrepl/op (meta v))]
          [(:name (:nrepl/op (meta v))) v])))

(defn- ^{:nrepl/op {:name "discover"
                    :doc "Discover known operations."}}
  discover [{:keys [transport] :as msg}]
  (t/send transport (m/response-for msg :status :done :value
                                    (for [[_ op-var] (ops)]
                                      (-> op-var meta :nrepl/op)))))

(defn wrap-discover-logic
  "Pulled out of wrap-discover so that it can be easily changed
  without restarting the JVM"
  [handler {:keys [transport op session] :as msg}]
  (if-let [discovered-handler ((ops) op)]
    (try (push-thread-bindings @session)
         (->> (discovered-handler msg) (apply concat)
              (apply m/response-for msg) (t/send transport))
         (catch Throwable e
           ;; I have no idea what *err* is bound to in this context,
           ;; but it's nothing I can see.
           (binding [*err* *out*]
             (clojure.repl/pst e 100))
           ;; Copied from nrepl-op
           (t/send transport (m/response-for msg :status #{:error :done}
                                             :message (.getMessage e)))
           (throw e))
         (finally (pop-thread-bindings)))
    (handler msg)))

(defn ^{:clojure.tools.nrepl.middleware/descriptor {:requires #{#'ses/session}}}
  wrap-discover [handler]
  (fn [msg] (#'wrap-discover-logic handler msg)))

