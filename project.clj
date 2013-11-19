(defproject troncle "0.1.1-SNAPSHOT"
  :description "A library of functions for quickly wrapping and executing clojure
code with tracing instrumentation from emacs."
  :url "https://github.com/coventry/troncle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [nrepl-discover "0.1.0"]
                 [org.clojars.coventry/tools.reader "0.8.0-SNAPSHOT"]
                 [caribou/clojure.walk2 "0.1.0"]]
  :repl-options {:nrepl-middleware [nrepl.discover/wrap-discover]})

