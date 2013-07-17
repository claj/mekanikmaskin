(ns mekanikmaskin.service
  "takes care of everything ATM
to be splitted up later, when reasonable intersections emerges

takes care of:
- setting up a sample db
- generate simple tasks
- handle user creation
- handle user logins by pedestal/ring sessions
- persisting results
- facilitate user interaction"
  (:import [clojure.lang Keyword]
           [org.jasypt.util.password StrongPasswordEncryptor])
  (:use [midje.sweet]
        [hiccup.core]
        [datomic.api :only [q db] :as d]
        [ring.middleware.session.store]
        [clojure.test])
  (:require [io.pedestal.service.interceptor  :refer [definterceptor defhandler]]
            [io.pedestal.service.http :as bootstrap]
            [io.pedestal.service.http.route :as route]
            [io.pedestal.service.http.body-params :as body-params]
            [io.pedestal.service.http.route.definition :refer [defroutes]]
            [io.pedestal.service.http.ring-middlewares :as middlewares]
            [ring.util.response :as ring-resp]            
            [ring.middleware.session.cookie :as cookie]
            [mekanikmaskin.logging :as log]
            [clojure.core.async :as async :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; database connectivity
(def uri 
  "In-mem datomic db for dev" 
  "datomic:mem://mekanikmaskin")

(defn list-of-users
  "returns a list of the users registrerd"
  [conn]
  (q '[:find ?name :where [_ :user/username ?name]] (db conn)))

(defn conditional-connect-db 
  "if there is no database or given :force true
the database is removed and reinitialized, 
assert that there are some users availiable"
  [uri &{:keys [force]}]
  ;;pre: uri should be an OK datomic uri
  {:post [(list-of-users %)]}
  (if (or (d/create-database uri) force)
      (do
        (d/delete-database uri)
        (d/create-database uri)
        (let [conn (d/connect uri)]
          @(d/transact conn (read-string (slurp "resources/db/schema.dtm")))
          @(d/transact conn (read-string (slurp "resources/db/fauxusers.dtm")))
          conn))
      ;otherwise just connect as usual
      (d/connect uri)))

(def conn (conditional-connect-db uri :force true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; schema generation

;; see above

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user creation
(def min-password-length 7)

(defn encrypt-password!
  "encrypts password to hash"
  [pwd]
  {:post [(= (count %) 64)]
   :pre [(>= (count pwd) min-password-length)]}
  (.encryptPassword 
   (StrongPasswordEncryptor.) pwd))

(defn password-ok?
  "check password against (one of many possible) passwordhash"
  [pwd pwdhash]
  (.checkPassword
   (StrongPasswordEncryptor.) pwd pwdhash))

(defn timestamp! [] (java.util.Date.))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user login / session handling

;; remeber that sessions is just a :session thingie in the request/response
;; how does this work in pedestal?
;; what does it store as sessions key? an UUID?
;; how is it matched to the browser? through the cookies automatically?

;;about cookies (from middle-ware wrap-cookies
;; Parses the cookies in the request map, then assocs the resulting map
;; to the :cookies key on the request.

;; Each cookie is represented as a map, with its value being held in the
;; :value key. A cookie may optionally contain a :path, :domain or :port
;; attribute.

;; To set cookies, add a map to the _ :cookies _ key on the response. The values
;; of the cookie map can either be strings, or maps containing the following
;; keys:

;; :value - the new value of the cookie
;; :path - the subpath the cookie is valid for
;; :domain - the domain the cookie is valid for
;; :max-age - the maximum age in seconds of the cookie
;; :expires - a date string at which the cookie will expire
;; :secure - set to true if the cookie is valid for HTTPS only
;; :http-only - set to true if the cookie is valid for HTTP only

;; there's a session store:
;; (ns ring.middleware.session.store
;;   "Common session store objects and functions.")

;; (defprotocol SessionStore
;;   (read-session [store key]
;;     "Read a session map from the store. If the key is not found, an empty map
;; is returned.")
;;   (write-session [store key data]
;;     "Write a session map to the store. Returns the (possibly changed) key under
;; which the data was stored. If the key is nil, the session is considered
;; to be new, and a fresh key should be generated.")
;;   (delete-session [store key]
;;     "Delete a session map from the store, and returns the session key. If the
;; returned key is nil, the session cookie will be removed."))

;; This protocol should be implemented by my datomic layer to keep the sessions connected to the users
;, how was it with the store? is this implicitly given when calling these functions? is it the Datomic db? maybe.

;; this is the inmem storage:
;; (ns ring.middleware.session.memory
;;   "In-memory session storage."
;;   (:use ring.middleware.session.store)
;;   (:import java.util.UUID))
;; (deftype MemoryStore [session-map] ->  SessionStore  <-
;;   (read-session [_ key]
;;     (@session-map key))
;;   (write-session [_ key data]
;;     (let [key (or key (str (UUID/randomUUID)))]
;;       (swap! session-map assoc key data)
;;       key))
;;   (delete-session [_ key]
;;     (swap! session-map dissoc key)
;;     nil))
;; (defn memory-store
;;   "Creates an in-memory session storage engine."
;;   ([] (memory-store (atom {})))
;;   ([session-atom] (MemoryStore. session-atom)))

;; also we have a Cookie store

;; (deftype CookieStore [secret-key]
;;   SessionStore
;;   (read-session [_ data]
;;     (if data (unseal secret-key data)))
;;   (write-session [_ _ data]
;;     (seal secret-key data))
;;   (delete-session [_ _]
;;     (seal secret-key {})))

;; seal = kvitto, sigill typ

;; so we need the Datomic base layer - to be able to read-session, write-session, delete-session etc
;; this thingie must be related to the user auth mechanism somehow?
;; is it so that the session later on get's connected to the users "token"?

(defn logged-in? 
  "is there a cookie for this username in the db?"
  [conn username]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(defn username->id [conn username]
  (ffirst (q '[:find ?id :where [?id :user/username ?username] :in $ ?username] (db conn) username)))

(defn username->id [conn username]
  (ffirst (q '[:find ?id :where [?id :user/username ?username] :in $ ?username] (db conn) username)))

(fact (username->id conn "kajsa") =not=> nil)

(defn verify-cookie [conn username cookie]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(logged-in? conn "kajsa") ;;returns a hashset with an id of the cookie if ok.

(defn add-cookie!
  "ah, how do ask a certain db what happends?, like get datom xxx out of this db...
"
  [conn username cookie]
  {:post [(logged-in? conn username)]}
  @(d/transact conn [{:db/id (d/tempid :db.part/user)
                    :session/cookie cookie
                    :session/user (d/tempid :db.part/user (username->id conn username))}]))

(add-cookie! conn "kajsa" "a2342hahah212")
;;returns
;;{:db-before datomic.db.Db@dfba11d8, 
;; :db-after datomic.db.Db@830b3e7f, 
;; :tx-data [
;;    #Datum{:e 13194139534317 :a 50 :v #inst "2013-06-16T15:24:44.644-00:00" :tx 13194139534317 :added true} 
;;    #Datum{:e 17592186045422 :a 64 :v "a2342hahah" :tx 13194139534317 :added true} 
;;    #Datum{:e 17592186045422 :a 65 :v 17592186045419 :tx 13194139534317 :added true}
;;], :tempids {-9223350046623220447 17592186045422}}

;;can we make sure that the cookie is added somehow?

(fact "that there now is a session cookie availiable"
 (q '[:find ?cookie :where [?cookie :session/cookie "a2342hahah"]] (db conn)) =not=> nil)

(fact "that there are users in the database and we can find them"
      (list-of-users) =not=> empty?)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; task creation

(defn query-maker
  "generates a simple math-query and the answer to it"
  []
  (let [a (rand-int 10) b (rand-int 10)]
    {:query (str "what is " a " + " b "?")
     :answer (+ a b)}))

;; {:query "what is 1 + 2 ?" :answer 3}

{:task "what is 10+10?"
 :exercise-id "exercise 123"
 :continuation {"a" 10 "b" 10}
 :answers [{:id "answerid 222221"
            :text "20"
            :correct true}
           {:id "answerid 222222"
            :text "0"
            :correct false}
           {:id "answerid 222223"
            :text "12"
            :correct false}
           {:id "answerid 222224"
            :text "100"
            :correct false}]}



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; answering

(defn valid-answer? 
  "constructs a set of all the ids and see if answer-id is in it"
  [answers answer-id]
  (not (nil? ((set (map :id answers)) answer-id))))

(deftest test-valid-answer?
  (is (valid-answer? [{:id 1}] 1))
  (is (not (valid-answer? [{:id 1} {:id 2}] 3))))

(defn correct-answer? 
  "selects the answer-id in the answers and checks it's correctness"
  [answers answer-id]
  (:correct (first (filter #(= (:id %) answer-id) answers))))

;;this shuld access task as everything else
(deftest correct-answer?
  "unvalid answers checked before"
  (let [answers       [{:id "ans 1" :correct true}]
        correct-reply "ans 1"
        wrong-reply   "ans 2"]
    (is (correct-answer? answers correct-reply))
    (is (not (correct-answer? answers wrong-reply)))))


(defn answer! 
  "attempts to answer a given exercise from incoming request somehow"
  [student answer-id])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; score aggregation


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; task history / state

(defn task->history!
  "just want to move the task at hand to history with it's answer added on and "
  [student answer]
  ;;assert that we can answer something at all!
  ;;ie we don't have a task at hand here!
  (dosync
   (let [task (task-at-hand student)
         valid? (valid-answer? task answer)
         correct? (correct-answer? (:answers task) answer)
         new-history-item {:timestamp (timestamp!) 
                           :correct correct? 
                           :valid valid? 
                           :task task}]
     ;;remove task
     (alter students-ref assoc-in [student :task-at-hand] nil)
     ;;add-it-to history with result and a timestamp!
     (alter students-ref update-in [student :history] conj new-history-item))))

(deftest "task->history"
  (task->history "pelle" "answerid 111111"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; web templating

(defn four-field 
  "extemely quick and dirty four field layouting function"
  [query answer-1 answer-2 answer-3 answer-4]
  (html 
   [:html 
    [:head [:title "four field"]]
    [:body
     [:h2 query]
     [:div {:style "border: solid; width: 10em;"} answer-1] " " 
     [:div {:style "border: solid; width: 10em;"} answer-2] " "
     [:div {:style "border: solid; width: 10em;"} answer-3] " "
     [:div {:style "border: solid; width: 10em;"} answer-4]]]))

(defn textbox-query 
  "query? 
   [_answer__] [OK]"
;; leads to :form-params {"hej" "234"}
  [query]
  (html [:html
         [:head [:title "textbox-query"]]
         [:body 
          [:h2 query]
          [:form { :action "/qbox" :method "POST"}
           [:input {:type "textbox" :name "value"}]
           [:input {:type "submit" }]]]]))

(defn about-page []
  (html [:html [:head [:title "About mekanikmaskinen"]]
         [:body
          [:h2 "about"]
          [:p "this is a super great project to teach you mechanics"]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; web request handling

(def url-for (route/url-for-routes routes))

(defn about-page
  [request]
  (ring-resp/response (about-page)))

(defn login! [request]
  (ring-resp/response "login confirmation placeholder"))

(defn exercise [request]
  (ring-resp/response (layouting/four-field "what is 2+2?" "1" "2" "3" "4") ))

(defn querybox [req]
  (ring-resp/response (layouting/textbox-query "what is 2+3?")))

(defn ans 
  "receieves an answer, returns yet another page?"
  [req]
  (let [value (str (get (:form-params req) "value"))]

    ;;if value was ok, render a new (harder) task
    ;;if value was not ok, render a new, easier task
    )
  (ring-resp/response (str @state-atom)))

(definterceptor session-interceptor
  (middlewares/session {:store (cookie/cookie-store)}))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) bootstrap/html-body] 
     ["/about" {:get about-page}]
     ["/login" ^:interceptors [middlewares/params middlewares/keyword-params session-interceptor] {:get login-page :post login!}]
     ["/exercise" {:get exercise}]
     ["/qbox" ^:interceptors [middlewares/params] {:get querybox :post ans}]]]])

;;; WASTELAND
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

(defn list-of-users
  "returns a list of the users registrerd"
  [conn]
  (q '[:find ?name :where [_ :user/username ?name]] (db conn)))

(defn username->id [conn username]
  (ffirst (q '[:find ?id :where [?id :user/username ?username] :in $ ?username] (db conn) username)))

(fact (username->id conn "kajsa") =not=> nil)

(defn logged-in? 
  "is there a cookie for this username in the db?"
  [conn username]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(defn verify-cookie [conn username cookie]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(logged-in? conn "kajsa") ;;returns a hashset with an id of the cookie if ok.

(defn add-cookie!
  "ah, how do ask a certain db what happends?, like get datom xxx out of this db...
"
  [conn username cookie]
  {:post [(logged-in? conn username)]}
  @(d/transact conn [{:db/id (d/tempid :db.part/user)
                    :session/cookie cookie
                    :session/user (d/tempid :db.part/user (username->id conn username))}]))


"task is the various exercises to be solved by students, 
hopefully to learn something
about the course(s) at hand
the individual task can only be solved once.
the "

;;Task properties:
;; can be rendered to the user (should contain enough info to be able to be...)
;; is either equivalent to the task in db or have some internal state vars

(defn new-task
  "this function should return a suitable (random) task from the user-state"
  [user])

{:exercise "what is 10+10?"
 :exercise-id "exercise 123"
 :continuation {"a" 10 "b" 10}
 :answers [{:id "answerid 222221"
            :text "20"
            :correct true}
           {:id "answerid 222222"
            :text "0"
            :correct false}
           {:id "answerid 222223"
            :text "12"
            :correct false}
           {:id "answerid 222224"
            :text "100"
            :correct false}]}

;;bindings is not the shit.

(defn task-bindings-gen
  "returns a random task binding
doesn't work as expected?"
 []
 {:post [(map? %), (number? (:a %)), (number? (:b %))]}
  (io!
   {:a (+ (rand-int 10) 10)
    :b (+ (rand-int 10) 10)}))

;;(with-bindings (task-bindings-gen)
;;  (str a " , " b))

;;how to store a macro in a sane way?

(let [things {:a 1 :b 2}]
  (str (:a things) ", " (:b things)))

;; want to persist state like this:

;; -generate state
;; -make task
;; save state
;; save task (which could be a part of the state as well, of course)
;; the task should be fully deterministic, thats all

(defn gen-task [{:keys [a b]}]
  {:exercise (str "what's " a " + " b "?")
   :answers [[:correct true :val (+ a b)]
             [:correct false :val (- a b)]
             [:correct false :val 0]
             [:correct false :val (- b a)]]})

;;how to get the typing right?

(defn gen-task3 [{:keys [a b c]}]
  {:exercise (format "what's %d+%d+%d?" a b c)})
(gen-task3 {:a 1 :b 2 :c 3})

(def fmt ["what's " :smallint :plus :smallint :plus :smallint "?"])

(apply str fmt)

(defn task-reader [task]
  (cond
    (string? task) task
    (keyword? task) (condp = task
                      :smallint 1
                      :plus \+
                      :minus \-
                      :largeint 20)))

(apply str (map task-reader fmt))

;;now: want to apply a state-storage for each :smallint etc
    

(defmulti taskreader (fn [x] [(type x) x]))

(defn dispatch [x] [(type x) x])

(defmethod taskreader [Keyword :smallint] [a]
  (let [x 1]
      {:string (str x) 
       :state x}))

(defmethod taskreader [Keyword :a] [a]
 "a")
(taskreader :a)
(taskreader :smallint)

(defmulti tr dispatch)
(defmethod tr [Keyword :a] [x] "aa!")
(defmethod tr [Keyword :b] [x] (rand-int 10))

(tr :a)
(tr :b)

(taskreader :smallint)
(taskreader :smallint)

;; seems to be my own reader...
  
;;there is the possibility to adress the variables by
;;the order

;; the state enough verbose to be useful is

[[:text "what is " [:latex [:var :named-a :smallint] " + " [:var :named-b :smallint]] "?"]]

;; how true to mathematical representation should the model be?

;; this is very similar to html, maybe?
;; the latex gets rendered as an image, and should be stored somewhere as such
;; the answers should be registrered somewhere for the user

;; answers must be dedicated to a certain task
;; a task must be dedicated to one certain session
;;the task coupled to a session is changing over time. maybe it's just an update of a certain position in a session datom?

;;or the sessions are actually differed - many persons can work on one task at a certain time. if they are working togheter - a session could therefore be a "work" as well.

(defn user-exists?
  "returns the hash-set of the user"
  [username]
  {:post [(<= (count %) 1)]}
  (let [conn (d/connect uri)]
    (q '[:find ?id 
         :where [?id :user/username ?username] 
         :in ?username, $] 
       username (db conn))))

(defn registrer-user! [username pwd pwd2]
  {:pre [(= pwd pwd2), (empty? (user-exists? username))]
   :post [(user-exists? username)]}
  (let [conn (d/connect uri)]
    @(d/transact conn [{:db/id (d/tempid :db.part/user)
                        :user/username username
                        :user/password (encrypt-password pwd)}])))

(defn valid-credentials? [username pwd]
  {:pre [user-exists? username]}
   (let [conn (d/connect uri)
         pwdhash (ffirst (q '[:find ?pwdhash :where 
                              [?id :user/username ?username]
                              [?id :user/password ?pwdhash]
                              :in ?username $]
                            username (db conn)))]
           (check-password pwd pwdhash)))

(registrer-user! "linus" "hahahaha" "hahahaha")
(valid-credentials? "linus" "hahahaha")
(valid-credentials? "amundsen" "polarisar")          

(defn remove-user! [username]
  ;;retract in a transaction
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async things.

;; an un-buffered channel:
(def answer-chan (chan))
 
(future ;;to let go of the repl
  (go ;;start async transaction
   (while true ;; run forever
     (let [task (query-maker)] ;; create a task
       (println (:query task)) ;; output query (apparently this could be put in a channel)
       (if (== (<! answer-chan) (:answer task)) ;;wait for a new entry in answer-chan
         (println "correct!")
         (println "wrong"))))))
 
;;short helper for answering
(defn ans [x]
  (>!! answer-chan x))
