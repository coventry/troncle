(defproject troncle "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]
                 [nrepl-discover "0.0.0"]
                 [tools.reader "0.8.0-SNAPSHOT"]
                 [com.stuartsierra/clojure.walk2 "0.1.0-SNAPSHOT"]]
  :repl-options {:nrepl-middleware [nrepl.discover/wrap-discover]})

