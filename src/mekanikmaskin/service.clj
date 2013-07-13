(ns mekanikmaskin.service
  "pedestal service template
this is where some magic happends (preferably as littl as possible)"
  (:require [io.pedestal.service.interceptor  :refer [definterceptor defhandler]]
            [io.pedestal.service.http :as bootstrap]
            [io.pedestal.service.http.route :as route]
            [io.pedestal.service.http.body-params :as body-params]
            [io.pedestal.service.http.route.definition :refer [defroutes]]
            [io.pedestal.service.http.ring-middlewares :as middlewares]
            [ring.util.response :as ring-resp]            
            [ring.middleware.session.cookie :as cookie]
            [mekanikmaskin.logging :as log]
            [mekanikmaskin.dbdev :as db]
            [mekanikmaskin.layouting :as layouting]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s" (clojure-version))))

(defn home-page
  [request]
  (ring-resp/response (str  "Mekanikmaskinen! current users: " (db/list-of-users db/conn))))

(defn login-page [request]
  (ring-resp/response "Log in here!"))

(defn login! [request]
  (ring-resp/response "login confirmation placeholder"))

(defn exercise [request]
  (ring-resp/response (layouting/four-field "what is 2+2?" "1" "2" "3" "4") ))

(definterceptor session-interceptor
  (middlewares/session {:store (cookie/cookie-store)}))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/login" ^:interceptors [middlewares/params middlewares/keyword-params session-interceptor] {:get login-page :post login!}]
     ["/exercise" {:get exercise}]]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by mekanik-pedestal-service-test.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes
              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})
