(ns whining.server
  (:require
    [rum.core :as rum]
    [immutant.web :as web]
    [compojure.core :as cj]
    [compojure.route :as cjr])
  (:import
    [org.joda.time DateTime]
    [org.joda.time.format DateTimeFormat]))

; "/"
; "/feed"
; "/post/:id"
; "/post/:id/pict.jpg"
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
      Finally, look at the size of this thing! It covers the good half of the screen, and it’s the most important half (the bottom) where you are most likely to be doing something. Somehow after 13 years Apple was convinced that volume indicator shouldn’t cover the center of the screen. Next year we will see an incoming call notification that is, well, a tiny notification instead of a whole screen. Maybe eventually they’ll figure that out for airpods too? It only needs to show two numbers, come on!"
      :pictures ["shop.jpg"]}
  {   :id       "456" 
      :created  #inst "2020-01-03"
      :author   "freetonik"
      :body     "Simple rule: leave content where it is. Do not move it around.
      Here I click on a particular dog image and immediately whole page gets relayouted, every image moves to another random place. It’s like I am looking at the whole new page"
      :pictures ["youtube.jpg"] }])

(def styles
  "body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';
    font-size: 1em;
    line-height: 140%;
    padding: 0.25em 1em;
    max-width: 640px;
    margin: 0 auto;
  }
  
  img {
    max-width:100%;
    height:auto;
  }
  
  hr {
    border: 0;
    height: 0;
    border-top: 1px solid rgba(0, 0, 0, 0.1);
    border-bottom: 1px solid rgba(255, 255, 255, 0.3);
    margin-bottom: 2em;
  }
  
  header {
    margin-bottom: 2em;
  }
  
  header h1 {
    margin-bottom: 0;
  }
  
  header p {
    /* Without this there's a tiny visual mismatch that bothers me */
    margin-left: 0.09em; 
  }
  
  .post {
    display: flex;
    justify-content: flex-start;
    margin-bottom: 4em;
  }
  
  .post_sidebar {
    text-align: center;
    margin-right: 20px;
    min-width: 50px;
    /* display: flex;
    flex-direction: column;
    justify-content: space-between; */
  }
  
  .post_sidebar a:visited {
    color: blue;
  }
  
  img.avatar {
    border-radius: 100%;
    width: 50px;
    height: 50px;
  }
  
  .meta {
    font-size: 0.75rem;
  }
  
  
  .author { 
    font-weight: bold;
  }
  
  footer {
    padding-top: 1em;
    margin-top: 1em;
    border-top: 1px dotted black;
  } ")

(def date-formatter (DateTimeFormat/forPattern "dd.MM.YYYY"))

(defn render-date [inst]
  (.print date-formatter (DateTime. inst)))

(rum/defc post [post]
  [:.post
    [:.post_sidebar
      [:img.avatar {:src (str "/i/" (:author post) ".jpg") }]]
    [:div
      (for [name (:pictures post)]
        [:img { :src (str "/post/" (:id post) "/" name)}])
      [:p [:span.author (:author post)] ": " (:body post)]
      [:p.meta (render-date (:created post)) " // " [:a {:href (str "/post/" (:id post))} "Ссылка"]]]])

(rum/defc page [title & children]
  [:html
    [:head 
      [:meta { :http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      [:title title]
      [:meta { :name "viewport" :content "width-device-width, initial-scale=1.0"}]
      [:style {:dangerouslySetInnerHTML { :__html styles }}]]
    [:body
      [:header
        [:h1 "Ворчанне ягнят:"]
        [:p#site_subtitle "Это текст, это ссылка. Не нажимайте на ссылку."]]
      children ]
    [:footer
      [:a { :href "https://twitter.com/nikitonsky" } "Nikita Prokopov"]
      [:a { :href "https://twitter.com/freetonik" } "Rakhim Davletkaliev"]
      [:br]
      [:a { :href "/feed" :rel "alternate" :type "application/rss+xml"} "RSS"]]
    [:script {:dangerouslySetInnerHTML {:__html
    "window.onload = function() {
      reloadSubtitle();
      document.getElementById('site_subtitle').onclick = reloadSubtitle;
    }
    
    function reloadSubtitle() {
      var subtitles = [
        'Вы уверены, что хотите отменить? - Да / Нет / Отмена',
        'Select purchase to purchase for $0.00 - PURCHASE / CANCEL',
        'Это не текст, это ссылка. Не нажимайте на ссылку.',
        'Не обновляйте эту страницу! Не нажимайте НАЗАД',
        'Произошла ошибка ОК',
        'Пароль должен содержать заглавную букву и специальный символ'
      ];
    var subtitle = subtitles[Math.floor(Math.random() * subtitles.length)];
    var div = document.getElementById('site_subtitle');
    div.innerHTML = subtitle;
    }"}}]])

(rum/defc index [posts]
  (page "Ворчание ягнят:"
      (for [p posts]
        (post p))))

(defn render-html [component]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup component)))

(cj/defroutes routes
  (cjr/resources "/i" {:root "public/i"})
  (cj/GET "/" [:as req]
    { :body (render-html (index posts)) })
  (cj/GET "/write" [:as req]
    { :body "WRITE" })
  (cj/POST "/write" [:as req]
    { :body "POST" }))

(defn with-headers [handler headers]
  (fn [request]
    (some-> (handler request)
     (update :headers merge headers))))

(def app 
  (-> routes
    (with-headers { "Cache-Control" "no-cache"
                    "Expires"       "-1" 
                    "Content-Type" "text/html; charset=UTF-8"})))

(defn -main [& args]
  (let [args-map (apply array-map args)
        port-str (or  (get args-map "-p")
                      (get args-map "--port")
                      "7070")]
  (web/run #'app { :port (Integer/parseInt port-str) })))

(comment 
  (def server (-main "--port" "7070"))
  (web/stop server))