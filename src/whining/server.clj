(ns whining.server
  (:require
    [compojure.route]
    [rum.core :as rum]
    [ring.util.response]
    [clojure.edn :as edn]
    [immutant.web :as web]
    [clojure.java.io :as io]
    [compojure.core :as compojure])
  (:import
    [org.joda.time DateTime]
    [org.joda.time.format DateTimeFormat]))


(def app 
  (fn [req]
    { :status 200
      :body (:uri req) }))

(def styles (slurp (io/resource "style.css")))
(def script (slurp (io/resource "script.js")))


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
    [:script {:dangerouslySetInnerHTML {:__html script}}]])


(rum/defc index [post_ids]
  (page "Ворчание ягнят:"
      (for [post_id post_ids
            :let [path  (str "posts/" post_id "/post.edn")
                  p     (-> (io/file path)
                            (slurp)
                            (edn/read-string))]]
        (post p))))


(defn post-ids [] 
  (for [name (seq (.list (io/file "posts")))
        :let [child (io/file "posts" name)]
        :when (.isDirectory child)]
      name))


(defn render-html [component]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup component)))


; "/"
; "/feed"
; "/post/:id"
; "/post/:id/pict.jpg"
; "/login"
; GET "/write"
; POST "/write"


(compojure/defroutes routes
  (compojure.route/resources "/i" {:root "public/i"})

  (compojure/GET "/" []
    { :body (render-html (index (post-ids))) })

  (compojure/GET "/post/:id/:img" [id img]
    (ring.util.response/file-response (str "posts/" id "/" img)))

  ; (compojure/GET "/write" []
  ;   { :body "WRITE" })

  ; (compojure/POST "/write" [:as req]
  ;   { :body "POST" })
    )


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
  (println "Starting webserver on port " port-str)
  (web/run #'app { :port (Integer/parseInt port-str) })))


(comment 
  (def server (-main "--port" "7070"))
  (web/stop server))