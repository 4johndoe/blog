(ns whining.server
  (:require
    [rum.core :as rum]
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

(def posts
  [{  :id       "123" 
      :created  #inst "2020-01-01"
      :author   "nikitonsky"
      :body     "One of the most useless and most annoying UIs in iPhone. This window pop ups on a random phone in the room if you open the case (no, not only on the phones it has been paired to—literally on any phone in proximity).
      But then it doesn’t reliably pop up when you actually need it (of course!). Sometimes I spend a few literal minutes opening and closing the case like a fool, putting airpods in or out, turning the phone on or off, in the hope it will finally show. This is stupid, because Airpods are connected via bluetooth to the phone already! I can listen for the audio, but can’t see the battery level, because, well, nobody knows why and there’s no button to press to force it to show.
      Finally, look at the size of this thing! It covers the good half of the screen, and it’s the most important half (the bottom) where you are most likely to be doing something. Somehow after 13 years Apple was convinced that volume indicator shouldn’t cover the center of the screen. Next year we will see an incoming call notification that is, well, a tiny notification instead of a whole screen. Maybe eventually they’ll figure that out for airpods too? It only needs to show two numbers, come on!"}
  {   :id       "456" 
      :created  #inst "2020-01-03"
      :author   "freetonik"
      :body     "Simple rule: leave content where it is. Do not move it around.
      Here I click on a particular dog image and immediately whole page gets relayouted, every image moves to another random place. It’s like I am looking at the whole new page"}])

(rum/defc post [post]
  [:.post
    [:.post_sidebar
      [:img.avatar {:src "/i/" (:author post) ".jpg"}]]
    [:div
      [:p [:span.author (:author post)] ": " (:body post)]
      [:p.meta (:created post) "//" [:a (:href (str "/post/" (:id post)))]]]])

(rum/defc index [posts]
  [:html
    [:body
      (for [p posts]
        (post p))]])

(cj/defroutes routes
  (cj/GET "/" [:as req]
    { :body (rum/render-static-markup (index posts)) })
  (cj/GET "/write" [:as req]
    { :body "WRITE" })
  (cj/POST "/write" [:as req]
    { :body "POST" }))

(def app routes)

(defn -main [& args]
  (let [args-map (apply array-map args)
        port-str (or  (get args-map "-p")
                      (get args-map "--port")
                      "7070")]
  (web/run #'app { :port (Integer/parseInt port-str) })))

(comment 
  (def server (-main "--port" "7070"))
  (web/stop server))