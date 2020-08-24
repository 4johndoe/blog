(ns whining.server
  (:require
    [compojure.route]
    [rum.core :as rum]
    [ring.util.response]
    [clojure.edn :as edn]
    [immutant.web :as web]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [ring.middleware.params]
    [compojure.core :as compojure]
    [clojure.java.shell :as shell]
    [ring.middleware.multipart-params]
    [ring.middleware.session :as session]
    [ring.middleware.session.cookie :as session.cookie])
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
(def authors { "helpdesk@gerchikco.com" "nikitonsky"})

(defonce *tokens (atom {}))

(defn zip [coll1 coll2]
  (map vector coll1 coll2))


(defn now []
  (java.util.Date.))


(defn render-date [inst]
  (.print date-formatter (DateTime. inst)))


(defn encode-uri-component [s]
  (-> s
      (java.net.URLEncoder/encode "UTF-8")
      (str/replace #"\+"    "%20")
      (str/replace #"\%21"  "!")
      (str/replace #"\%27"  "'")
      (str/replace #"\%28"  "(")
      (str/replace #"\%29"  ")")
      (str/replace #"\%7E"  "-")))


(defn redirect [url query]
  (let [query-str (when-not (empty? query)
                    (map 
                      (fn [[k v]]
                        (str (name k) "=" (encode-uri-component v)))
                      query))]
  { :status 302
    :headers { "Location" (if (some? query-str) 
                            (str url "?" query-str) 
                            url) }}))


(defn random-bytes [size]
  (let [seed (byte-array size)]
    (.nextBytes (java.security.SecureRandom.) seed)
    seed))


(defn save-bytes! [file bytes]
  (with-open [os (io/output-stream (io/file file))]
    (.write os bytes)))


(defn read-bytes [file len]
  (vec (with-open [is (io/input-stream (io/file file))]
    ; (prn is (.available is))
    (let [res (make-array Byte/TYPE len)]
      (.read is res 0 16)
      res))))


(defonce cookie-secret
  (if (.exists (io/file "COOKIE_SECRET"))
    (read-bytes "COOKIE_SECRET" 16)
    (let [bytes (random-bytes 16)]
      (save-bytes! "COOKIE_SECRET" bytes)
      bytes)))


(defn send-mail! [{:keys [to subject body]}]
  (shell/sh
    "mail"
    "-s"
    subject
    to
    "-a" "Content-Type: text/html"
    "-a" "From:Grumpy Admin <admin@grumpy.website>"
    :in body))

(send-mail! { :to "bogdandemchenko@gmail.com"
              :subject (str "Login to Grumpy " (rand))
              :body "<html>
                      <div style='text-align: center;'>
                        <a href='#' 
                           style='display: inline-block; 
                                  font-size: 16px; 
                                  padding: 0.5em 1.75em; 
                                  background: #c3c; 
                                  color: white; 
                                  text-decoration: none; 
                                  border-radius: 4px;'>
                          Login:</a></div></html>" })


(rum/defc post [post]
  [:.post
    [:.post_sidebar
      [:img.avatar {:src (str "/i/" (:author post) ".jpg") }]]
    [:div
      (for [name (:pictures post)]
        [:img { :src (str "/post/" (:id post) "/" name)}])
        
      (for [[p idx] (zip (str/split (:body post) #"\n+") (range))]
        [:p (when (== 0 idx)
              [:span.author (:author post) ": "])
            p])
      [:p.meta (render-date (:created post)) " // " [:a {:href (str "/post/" (:id post) )} "Ссылка"]
                                             " // " [:a {:href (str "/post/" (:id post) "/edit")} "edit"]]]])


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
            [:h1 title 
              [:a#add_post { :href "/new" }
                [:img#add_post { :src "/i/addNewPost.png" }] ]]
            [:h1 [:a {:href "/"} title]
              [:a#add_post { :href "/logout" }
                [:img#add_post { :src "/i/logout.png" }]]])
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


(def ^:const encode-table "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefjhijklmnopqrstuvwxyz")


(defn encode [num len]
  (loop [ num num
          list ()
          len len ]
    (if (== 0 len)
      (str/join list)
      (recur  (bit-shift-right num 6) 
              (let [ch (nth encode-table (bit-and num 0x3F))]
                (conj list ch))
              (dec len)))))


(defn next-post-id []
  (str
    (encode (quot (System/currentTimeMillis) 1000) 6)
    (encode (rand-int (* 64 64 64)) 3)))


(defn gen-token []
  (str
    (encode (rand-int (Integer/MAX_VALUE)) 5)
    (encode (rand-int (Integer/MAX_VALUE)) 5)))


(defn get-token [email]
  (:value (get @*tokens email))) ;; FIXME validate ts


(defn save-post! [post pictures]
  (let [dir           (io/file (str "posts/" (:id post)))
        picture-names (for [[picture idx] (zip pictures (range))
                            :let [in-name   (:filename picture)
                                  [_ ext]   (re-matches #".*(\.[^\.]+)" in-name)]]
                        (str (:id post) "_" (inc idx) ext))]
    (.mkdir dir)
    (doseq [[picture name] (zip pictures picture-names)]
      (io/copy (:tempfile picture) (io/file dir name))
      (.delete (:tempfile picture)))
    (spit (io/file dir "post.edn") (pr-str (assoc post :pictures (vec picture-names))))))


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
                :method "post"
                :enctype "multipart/form-data" }
        [:.edit_post_picture
          [:input { :type "file" :name "picture" }]]
        [:.edit_post_body
          [:textarea 
            { :value (:body post "") 
              :name "body"
              :placeholder "Пиши сюда ..."}]]
        [:.edit_post_submit
          [:button.btn (if create? "Создать" "Сохранить")]]])))


(rum/defc email-sent-page [message]
  (page {}
    [:div.email_sent_message message]))


(rum/defc forbidden-page [redirect]
  (page {}
    [:form {  :action "/send-email"
              :method "post"}
      [:div.forbidden_email
        [:input { :type "text" :name "email" :placeholder "E-mail" :value "helpdesk@gerchikco.com" }]] 
      [:div
        [:input { :type "text" :name "redirect" :value redirect }]]  ;; FIXME
      [:div
        [:button.btn "Отправить письмецо"]]]))
    ; [:a { :href (str "/authenticate?user=nikitonsky&token=ABC&redirect=" (encode-uri-component redirect)) }
    ;     "Login"]))


(defn post-ids [] 
  (->>
    (for [name (seq (.list (io/file "posts")))
          :let [child (io/file "posts" name)]
          :when (.isDirectory child)]
        name)
    (sort)
    (reverse)))


(defn render-html [component]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup component)))


; "/"
; "/feed"
; "/post/:id"
; "/post/:id/pict.jpg"
; "/login"
; GET "/new"
; POST "/new"


; (defn read-session [handler]
;   (fn [req]
;     (let [session (some-> (get-in req [:cookies "session" :value])
;                               (edn/read-string))]
;       (handler (if (some? session)
;                     (assoc req :user (:user session))
;                     req)))))


(defn check-session [req]
  (when (nil? (get-in req [:session :user]))
    { :status 302
      :headers {  "Location" (str "/forbidden?redirect=" (encode-uri-component (:uri req)))  }} ))


(compojure/defroutes protected-routes
  (compojure/GET "/new" [:as req]
    (or
      (check-session req)
      { :status 303
        :headers { "Location" (str "/post/" (next-post-id) "/edit") }}))
  
  (compojure/GET "/post/:id/edit" [id :as req]
    (or
      (check-session req)
      { :body (render-html (edit-post-page id)) }))
  
  (ring.middleware.multipart-params/wrap-multipart-params
      (compojure/POST "/post/:id/edit" [id :as req]
        (or 
          (check-session req)
          (let [params  (:multipart-params req)
                body    (get params "body")
                picture (get params "picture")]
              (save-post! { :id id
                            :body body
                            :author (get-in req [:session :user])
                            :created (now) }
                          [picture])
              { :status 303
              :headers { "Location" (str "/post/" id) }})))))


(compojure/defroutes routes
  (compojure.route/resources "/i" {:root "public/i"})

  (compojure/GET "/" []
    { :body (render-html (index-page (post-ids))) })

  (compojure/GET "/post/:id/:img" [id img]
    (ring.util.response/file-response (str "posts/" id "/" img)))

  (compojure/GET "/post/:id" [id]
    { :body (render-html (post-page id)) })


  (compojure/GET "/forbidden" [:as req]
    { :body (render-html (forbidden-page (get (:params req) "redirect"))) })

  
  (compojure/GET "/authenticate" [:as req] ;; ?email=...&token=..&redirect=...
    (let [email     (get (:params req) "email")
          user      (get authors email)
          token     (get (:params req) "token")
          redirect  (get (:params req) "redirect")]
      (if (= token (get-token email))
        { :status 302
          :headers { "Location" redirect }
          :session {  :user     user
                      :created  (now) }}
        { :status 403
          :body "403 Bad token" })))


  (compojure/GET "/logout" [:as req]
    { :status 302
          :headers { "Location" "/" }
          :session nil } )

  (compojure/POST "/send-email" [:as req]
    (let [params    (:params req)
          email     (get params "email")
          redirect  (get params "redirect")
          token     (gen-token)
          link      (str  (name (:scheme req))  
                          "://"
                          (:server-name req)
                          (when (not= (:server-port req) 80)
                            (str ":" (:server-port req)))
                          "/authenticate" 
                          "?email=" (encode-uri-component email) 
                          "&token=" (encode-uri-component token)
                          "&redirect=" (encode-uri-component redirect))]
      (swap! *tokens assoc email { :value token :created (now) })
      { :status 302
        :headers {  "Location" (str "/email-sent?message=" (encode-uri-component link)) } }))

  (compojure/GET "/email-sent" [:as req]
    { :body (render-html (email-sent-page (get-in req [:params "message"]))) })

  protected-routes

  (fn [req]
    { :status 404
      :body "Not found" }))


(defn with-headers [handler headers]
  (fn [request]
    (some-> (handler request)
     (update :headers merge headers))))


(defn print-errors [handler]
  (fn [req]
    (try 
      (handler req)
      (catch Exception e
        (.printStackTrace e)
        { :status 500
          :headers { "Content-Type" "text/html; charset=UTF-8" }
          :body (with-out-str
                  (clojure.stacktrace/print-stack-trace (clojure.stacktrace/root-cause e))) }))))


(def app 
  (-> 
    routes
    (session/wrap-session
      { :store (session.cookie/cookie-store { :key cookie-secret })
        :cookie-name "grumpy"
        :cookie-attrs { :http-only  true
                        :secure     false ;; FIXME
                        ; :max-age    ;; FIXME
                      } })
    (ring.middleware.params/wrap-params)
    (with-headers { "Cache-Control" "no-cache"
                    "Expires"       "-1" 
                    "Content-Type" "text/html; charset=UTF-8"})
  (print-errors)))


(defn -main [& args]
  (let [args-map (apply array-map args)
        port-str (or  (get args-map "-p")
                      (get args-map "--port")
                      "7070")]
  (println "Starting webserver on port " port-str)
  (web/run #'app { :port (Integer/parseInt port-str) })))


(comment 
  (def server (-main "--port" "7070"))
  (web/stop server)
  ; (if (.exists (io/file "COOKIE_SECRET"))
  ;   (io/delete-file "COOKIE_SECRET"))
)