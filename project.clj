(defproject whining "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [
    [org.clojure/clojure        "1.10.1"]
    [compojure                  "1.6.2"]
    [ring/ring-core             "1.8.1"]
    [org.immutant/web           "2.1.10"]
    [rum                        "0.12.3"]
    [org.clojure/clojurescript  "1.10.773"]]
  :repl-options {:init-ns whining.server})
