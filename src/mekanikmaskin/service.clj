(ns mekanikmaskin.service
  "takes care of everything ATM
to be splitted up later, when reasonable intersections emerges

takes care of:
- setting up a sample db
- generate simple tasks
- handle user creation
- handle user logins by pedestal/ring sessions
- persisting results
- facilitate user interaction

how to store tasks of various types in datomic? by the ref - it's untyped and we can have a
protocol or whatever in the other end showing the task correctly

   A student logs in
   and
   gets the first task
   answers this task
   right: one way
   wrong: another way

   until all concepts are found to be mastered by the student

   now we can be sure the student knows enough to finish the course.

four different heuristics:
 - list of ''good next task'' - recommendations.
 - concept navigation
 - failrate (accumulated thorugh users / NN for various neighbours)
 - flowrate / we want you in flow / time derivative?
"
  (:import [clojure.lang Keyword]
           [org.jasypt.util.password StrongPasswordEncryptor])
  (:use [midje.sweet]
        [clojure.test]
        [hiccup.core]
        [datomic.api :only [q db] :as d]
        [ring.middleware.session.store]
        [clojure.repl])
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

;; test in the function definition: greatz

;; http://clojuredocs.org/clojure_core/1.2.0/clojure.core/test
;;(defn my-function
;;"this function adds two numbers"
;;  {:test #(do
;;         (assert (= (my-function 2 3) 5))
;;         (assert (= (my-function 4 4) 8)))}
;; ([x y] (+ x y)))
;; 	 
;;(test #'my-function)  ;equal to (test (var my-function))
;;=> :ok
;; 	 
;;(defn my-function
;;"this function adds two numbers"
;;{:test #(do
;;           (assert (= (my-function 2 3) 5))
;;           (assert (= (my-function 99 4) 8)))}
;;  ([x y] (+ x y)))
;; 	 
;;(test #'my-function)
;; 	=> java.lang.AssertionError: Assert failed: (= (my-function 99 4) 8) (NO_SOURCE_FILE:0

;;(defn my-function
;;"this function adds two numbers"
;;([x y] (+ x y)))
;; 	 
;;(test #'my-function)
;;=> :no-test

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; database connectivity, initialization

;; see schema in ./resources/db/schema.dtm
(def uri 
  "In-mem datomic db for dev" 
  "datomic:mem://mekanikmaskin")

(declare list-of-users)

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
;; User
;; a user can
;; be created
;; be deleted
;; change password


(def min-password-length 7)

(defn encrypt-password!
  "encrypts password to hash, the jasypt StrongPasswordEncryptor hash is not giving the same hash for the same password (for security reasons), therefore the ! in the name"
  [^String pwd]
  {:post [(= (count %) 64)] ;;the jasypt returns the password hash value as an encoded string
   :pre [(>= (count pwd) min-password-length)]} ;;the password must be of sufficent length
  (.encryptPassword 
   (StrongPasswordEncryptor.) pwd))

(defn password-ok?
  "check password against (one of many possible) passwordhash"
  [^String pwd ^String pwdhash]
  {:pre [(= (count pwdhash) 64) 
         (>= (count pwd) min-password-length)]}
  (.checkPassword
   (StrongPasswordEncryptor.) pwd pwdhash))

(defn user-exists?
  "returns the hash-set of the user"
  [^String username]
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
                        :user/password (encrypt-password! pwd)}])))

(defn valid-credentials? [username pwd]
  {:pre [user-exists? username]}
   (let [conn (d/connect uri)
         pwdhash (ffirst (q '[:find ?pwdhash :where 
                              [?id :user/username ?username]
                              [?id :user/password ?pwdhash]
                              :in ?username $]
                            username (db conn)))]
           (password-ok? pwd pwdhash)))

(registrer-user! "linus" "hahahaha" "hahahaha")
(valid-credentials? "linus" "hahahaha")
(valid-credentials? "amundsen" "polarisar")

(defn remove-user! [username]
  ;;retract in a transaction
)

(defn list-of-users
  "returns a list of the users registrerd"
  [^datomic.peer.LocalConnection conn]
  {:post [(= (type %) java.util.HashSet) (not (zero? (count %)))]}
  (q '[:find ?name :where [_ :user/username ?name]] (db conn)))

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

;;the protocol is like this:
;;
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

;;are these really nesc?

(defn username->id [conn username]
  (ffirst (q '[:find ?id :where [?id :user/username ?username] :in $ ?username] (db conn) username)))

(fact (username->id conn "kajsa") =not=> nil)

(defn verify-session [conn username cookie]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(logged-in? conn "kajsa") ;;returns a hashset with an id of the cookie if ok.
;;=> {}


(deftype DatomicStore [uuid]
  SessionStore
  (read-session [uuid data]
    nil ;; query for datomic session here
    ;;what is data here?
    )
  (write-session [uuid _ data]
    nil
    ;;transact session datom here
    )
  (delete-session [uuid _]
    nil
    ;;retract session datom here
    ))


;; [:db/retract entity-id attribute value]
;; [data-fn args*]

;; Each map a transaction contains is equivalent to a set of one or more :db/add operations. The map must include a :db/id key identifying the entity data is being added to (as described below). It may include any number of attribute, value pairs.

;; {:db/id entity-id
;;  attribute value
;;  attribute value
;;  ... }

;; Internally, the map structure gets transformed to the list structure. Each attribute, value pair becomes a :db/add list using the entity-id value associated with the :db/id key.

;; [:db/add entity-id attribute value]
;; [:db/add entity-id attribute value]
;; ...

;; The map structure is supported as a convenience when adding data. As a further convenience, the attribute keys in the map may be either keywords or strings. 

(defn add-cookie!
  "ah, how do ask a certain db what happends?, like get datom xxx out of this db..."
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

(defprotocol Task
  (generate [this options]
    "generate a task given a map options")
  (correct? [this answer]
    "answers 'is the given answer correct for this task?'"))


;; a task can be
;;    rendered.

;; dummy version of a task just to see how gorgeous it is to have automatic rendering of 'em
(defprotocol TaskRender 
  (to-html [task] "generates an html version of the task")
  (to-text [task] "genereates a string version of the task"))

;; textbox-query:
{
 :query "what is 2+2?"
 :exercise-id "exercise 1245"
 :answer "4"}

;; fourfield-query:

{:query "what is 10+10?"
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

(defprotocol RenderT
		(to-text [this])
		(to-html [this]))

(deftype FourFieldT [a b]
		RenderT
		(to-text [task] (str "what is " a " + " b "?"))
		(to-html [task] (str "<h1>what is " a " + " b "?</h1>")))

(defn textbox-query-to-html
  "query? 
   [_answer__] [OK]"
  [query]
  (html [:html
         [:head [:title "textbox-query"]]
         [:body 
          [:h2 query]
          [:form { :action "/qbox" :method "POST"}
           [:input {:type "textbox" :name "value"}]
           [:input {:type "submit" }]]]]))


(defn textbox-query-to-tex [query]
  (str (:query query)))

(defn four-field-query-to-html
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

(deftype FourField [task] Task TaskRender
;;         (generate [task options]) this should be in a factory or something instead!
         (correct? [task answer])
         (to-html [task])Ä)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; score aggregation / select new task

;; given that the user has solved task1, task2 is suitable.
;; or searching towards a certain goal?
;; some A* thing.


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

(defn add-cookie!
  "ah, how do ask a certain db what happends?, like get datom xxx out of this db..."
  [conn username cookie]
  {:post [(logged-in? conn username)]}
  @(d/transact conn [{:db/id (d/tempid :db.part/user)
                    :session/cookie cookie
                    :session/user (d/tempid :db.part/user (username->id conn username))}]))


;;"task is the various exercises to be solved by students, 
;;hopefully to learn something
;;about the course(s) at hand
;;the individual task can only be solved once.
;;the "

;;Task properties:
;; can be rendered to the user (should contain enough info to be able to be...)
;; is either equivalent to the task in db or have some internal state vars


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
             [:correct false :val 0] ;;but this is not nesc false!
             [:correct false :val (- b a)]]})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async things.

;; all the parts are here (almost) - we need to save the student state as well.


(defn query-maker []
  (let [a (rand-int 10)
        b (rand-int 10)]
    {:query (str "what is " a " + " b "?")
     :answer (str (+ a b))}))

;; ___this_should_be_setup_per_user:___

(def user-example 
  (let [query-chan (chan)
        answer-chan (chan)
        result-chan (chan)
        running (atom true)
        ]
    {:username "linus"
     :query-chan query-chan
     :answer-chan answer-chan
     :result-chan result-chan
     :status running
     :loop (future (go (while @running
                         (let [task (query-maker)]
                           (>! query-chan (:query task))
                           (>! result-chan (if (= (<! answer-chan) (:answer task))
                                             "correct!" "wrong"))))))
     :answer-fn (fn 
                  ([]     
                     (println  " query: " (<!! query-chan)))
                  ([what]  
                     (>!! answer-chan (str what)) 
                     (println "result:" (<!! result-chan)) 
                     (println "query :" (<!! query-chan))))}))
;; a query channel
(def query-chan (chan))

;; an answer channel:
(def answer-chan (chan))

;; results are delivered this way:
(def result-chan (chan))

;;this should be setup per user.
 
(future                        ;; to let go of the repl
  (go                          ;; start async transaction
   (while true                 ;; run forever  - while we have session  - logged in somewhere
     (let [task (query-maker)] ;; create a task
       (>! query-chan (:query task)) ;; output query to query-chan
       (>! result-chan (if (= (<! answer-chan) (:answer task)) ;;wait for a new entry in answer-chan
                         "correct" "wrong"))))))
 
(defn answer 
  ([] (println (<!! result-chan) " : " (<!! query-chan)))
  ([what]
                 (do (>!! answer-chan (str what)) (answer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; testing if something is likely to be a datom
 

(defn decent-datom?
  "testing that something seems to be a decent datom, catching it early on"
  {:test [(decent-datom? {:db/id (d/tempid :db.part/user )})]}
  [datom]
  (and 
   (map? datom)
   (= (type (:db/id datom)) datomic.db.DbId)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;; Defining lessions

 ;; one way to define lessions.

 ;; there should be some kind of relation between the various things here:

 ;; (lession "signes-lession"
 ;;   ;;implicit ordering below
 ;;   (fourfield "10 + 10?" :guessing 10 :correct 20 :minusive 0 :plusive 100 :multiplicative)
 ;;   (fourfield "10 * 10?" :guessing 10 :plusive 20 :guessing 0 :correct 100)
 ;;   (fourfield "10 - 10?" :guessing 10 :minusive 20 :correct 0 :guessing 100)
 ;;   (walkthrough :multiplicative "remeber that 2 * 3 is the same as taking 2+2-2")
 ;;   (walkthrough :plusive "remeber that adding up numbers goes like this: ")
 ;;   (yesno "is 100 more than 45?"))


;; this becames something like a
;; [lession: "name of lession"]
;; [task: fourfield, in lession above, query, answers [[][][][]]] 
;; etc...
;; and when the various answers... 
;;
;;



(defn fourfield [query & ansmap]
  (let [ansmap (apply hash-map ansmap)
        ;;generate 
        answerids (repeatedly #(d/tempid :db.part/user) (count ansmap))]
    ;;create answer ids and relate them to this particular task
    {:db/id (d/tempid :db.part/user)
     :task.fourfield/query query
     }
    ))



;; ok so for each answer there should be a pointer to a complete answer, with a heuristic guess

(defn answer [answer cause])

;; ok so for each cause there should come a next query/or walkthrough

(defn method-for-cause [cause]

)

;;and for each cause in a certain task there should be an identifier.

;; possibly you could learn what gave this error by nearest neighbouring a lot of students doing things wrongly here.

;; also, there could be a range of defined methods for making user interaction, like

'walkthrough
'yesno
'forfield
'freeform

;;that could show the various interaction needed. cool.

(defn lession 
  "should spit out a suitable datomic transaction to store this defined lession"
[name & forms]
  [name forms]

;;this should have a form walk like "get to the list items in forms containing "walkthrough symbol" else check in the lession stack in the database
;; the 

;; :guessing 10 is an indicator how we should handle guessing - just recover?
)

(lession "hej" "af" "f")
;;probably we get datom like this:


(def relation-vector [[:task1 0.9 :statmech]
                      [:task1 0.5 :math]
                      [:task2 0.4 :statmech]
                      [:task3 0.6 :statmech]
                      [:task3 0.9 :math]
])

(q '[:find ?task ?w1 ?w2 :where [?task ?w1 ?subj] [:task3 ?w2 ?subj]] relation-vector)

;;                     [current fail  ok
(def next-task-vector [[:task1 :task2 :task3]
                       [:task2 :task4 :task3]
                       [:task4 :task5 :task6]
                       [:task3 :task7 :task9]
                       [:task7 :task10 :task 11]])

(q '[:find ?task :where ]) ;; men hur var det nu man slog i regler?


(defn create-answer 
  "returns a datom containing an answer, pointing to the function instanciating it"
  {:post [#(decent-datom? %)]}
[ ^String text ^Boolean correct reasons ]
 {:db/id (d/tempid :db.part/user)
  :task.fourfield.answer/correct correct
  :task.fourfield.answer/reasons reasons
  :task.fourfield.answer/text text
}
)

(create-answer "2" true [])

;;should use the reverse attributes here...
