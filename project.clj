(defproject grumpy "0.1.0-SNAPSHOT"
  :dependencies [
    [org.clojure/clojure        "1.10.1"]
    [compojure                  "1.6.2"]
    [ring/ring-core             "1.8.1"]
    [org.immutant/web           "2.1.10"]
    [rum                        "0.12.3"]
    [org.clojure/clojurescript  "1.10.773"]
    [clojure.joda-time "0.7.0"]
]
:global-vars {*warn-on-reflection* true}
:main grumpy.server
:profiles {
  :userjar {
    :aot [grumpy.server]
    :uberjar-name "grumpy.jar"
  }
})
