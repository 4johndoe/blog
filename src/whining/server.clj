(ns whining.server
  (:require
    [immutant.web :as web]
    [compojure.core :as cj]
    [compojure.route :as cjr]))

; "/"
; "/post/:id"
; "/login"
; GET "/write"
; POST "/write"

(def app 
  (fn [req]
    { :status 200
      :body (:uri req) }))

(cj/defroutes routes
  (cj/GET "/" [:as req]
    { :body "INDEX" })
  (cj/GET "/write" [:as req]
    { :body "WRITE" })
  (cj/POST "/write" [:as req]
    { :body "POST" }))

(defn -main [& args]
  (let [args-map (apply array-map args)
        port-str (or  (get args-map "-p")
                      (get args-map "--port")
                      "7070")]
  (web/run #'app { :port (Integer/parseInt port-str) })))

(comment 
  (def server (-main "--port" "7070"))
  (web/stop server))