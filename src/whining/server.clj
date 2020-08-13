(ns whining.server
  (:require
    [rum.core :as rum]
    [ring.util.response]
    [clojure.edn :as edn]
    [immutant.web :as web]
    [compojure.core :as cj]
    [clojure.java.io :as io]
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

(def post_ids ["123" "456"])

(def styles
  (slurp (io/resource "style.css")))

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

(rum/defc index [post_ids]
  (page "Ворчание ягнят:"
      (for [post_id post_ids
            :let [path  (str "posts/" post_id "/post.edn")
                  p     (-> (io/file path)
                            (slurp)
                            (edn/read-string))]]
        (post p))))

(defn render-html [component]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup component)))

(cj/defroutes routes
  (cjr/resources "/i" {:root "public/i"})

  (cj/GET "/" [:as req]
    { :body (render-html (index post_ids)) })

  (cj/GET "/post/:id/:img" [id img]
    (ring.util.response/file-response (str "posts/" id "/" img)))

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