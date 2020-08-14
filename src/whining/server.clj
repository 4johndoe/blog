(ns whining.server
  (:require
    [compojure.route]
    [rum.core :as rum]
    [ring.util.response]
    [clojure.edn :as edn]
    [immutant.web :as web]
    [clojure.java.io :as io]
    [ring.middleware.params]
    [compojure.core :as compojure])
  (:import
    [java.util UUID]
    [org.joda.time DateTime]
    [org.joda.time.format DateTimeFormat])
  (:gen-class))


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


(rum/defc page [opts & children]
 (let [{:keys [title index?]
        :or {title    "Ворчание ягнят"
              index?  false}} opts]
    [:html
      [:head 
        [:meta { :http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
        [:title title]
        [:meta { :name "viewport" :content "width-device-width, initial-scale=1.0"}]
        [:style {:dangerouslySetInnerHTML { :__html styles }}]]
      [:body
        [:header
          (if index?
            [:h1 title]
            [:h1 [:a {:href "/"} title]])
          [:p#site_subtitle "Это текст, это ссылка. Не нажимайте на ссылку."]]
        children ]
      [:footer
        [:a { :href "https://twitter.com/nikitonsky" } "Nikita Prokopov"]
        [:a { :href "https://twitter.com/freetonik" } "Rakhim Davletkaliev"]
        [:br]
        [:a { :href "/feed" :rel "alternate" :type "application/rss+xml"} "RSS"]]
      [:script {:dangerouslySetInnerHTML {:__html script}}]]))


(defn safe-slurp [source]
  (try
    (slurp source)
    (catch Exception e
      nil)))


(defn get-post [post_id]
  (let [path (str "posts/" post_id "/post.edn")]
    (some-> (io/file path)
        (safe-slurp)
        (edn/read-string))))


(defn next-post-id []
  (let [uuid      (UUID/randomUUID)
        time      (int (/ (System/currentTimeMillis) 1000))
        high      (.getMostSignificantBits uuid)
        low       (.getLeastSignificantBits uuid)
        new-high  (bit-or (bit-and high 0x00000000FFFFFFFF)
                          (bit-shift-left time 32)) ]
    (str (UUID. new-high low))))


(defn save-post! [post]
  (let [dir (io/file (str "posts/" (:id post)))]
    (.mkdir dir)
    (spit (io/file dir "post.edn") (pr-str post))))


(rum/defc index-page [post_ids]
  (page {:index? true}
      (for [post_id post_ids]
        (post (get-post post_id)))))


(rum/defc post-page [post_id]
  (page {}
      (post (get-post post_id))))


(rum/defc edit-post-page [post_id]
  (let [post (get-post post_id)
        create? (nil? post)]
    (page {:title (if create? "Создание" "Редактирование")}
      [:form {  :action (str "/post/" post_id "/edit")
                :method "post" }
        [:textarea.edit_post_body 
          { :value (:body post "") 
            :name "body"
            :placeholder "Пиши сюда ..."}]
        [:input.edit_post_submit 
          { :type "submit" }
          (if create? "Создать" "Сохранить")]])))


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
    { :body (render-html (index-page (post-ids))) })

  (compojure/GET "/post/new" []
    { :status 303
      :headers { "Location" (str "/post/" (next-post-id) "/edit") }})  

  (compojure/GET "/post/:id/:img" [id img]
    (ring.util.response/file-response (str "posts/" id "/" img)))

  (compojure/GET "/post/:id" [id]
    { :body (render-html (post-page id)) })

  (compojure/GET "/post/:id/edit" [id]
    { :body (render-html (edit-post-page id)) })

  (compojure/POST "/post/:id/edit" [id :as req]
    (let [params (:form-params req)
          body   (get params "body")]
        (save-post! { :id id
                      :body body
                      :author "nikitonsky" }) ;; FIXME author
        { :status 303
        :headers { "Location" (str "/post/" id) }}))
  
  (fn [req]
    { :status 404
      :body "Not found" }))


(defn with-headers [handler headers]
  (fn [request]
    (some-> (handler request)
     (update :headers merge headers))))


(def app 
  (-> routes
    (ring.middleware.params/wrap-params)
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