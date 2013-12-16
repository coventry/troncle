(defproject troncle "0.1.2-SNAPSHOT"
  :description "A library of functions for quickly wrapping and executing clojure
code with tracing instrumentation from emacs."
  :url "https://github.com/coventry/troncle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojars.coventry/tools.reader "0.8.0-SNAPSHOT"]
                 [org.clojure/tools.trace "0.7.6"]
                 [caribou/clojure.walk2 "0.1.0"]
                 [org.clojure/core.async "0.1.262.0-151b23-alpha"]]
  :repl-options {:nrepl-middleware [troncle.discover/wrap-discover]})

