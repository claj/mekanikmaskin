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
- selecting new suitable tasks

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
  (:import [org.jasypt.util.password StrongPasswordEncryptor])
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
            [io.pedestal.service.http.route.definition :refer [expand-routes defroutes]]
            [io.pedestal.service.http.ring-middlewares :as middlewares]
            [ring.util.response :as ring-resp]            
            [ring.middleware.session.cookie :as cookie]
            [mekanikmaskin.logging :as log]
            [clojure.core.async :as async :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; database connectivity, initialization

;; see schema in ./resources/db/schema.dtm
(def uri 
  "In-mem datomic db for dev" 
  "datomic:mem://mekanikmaskin")

(defn list-of-users
  "returns a list of the users registrerd"
  [^datomic.peer.LocalConnection conn]
  {:post [(= (type %) java.util.HashSet) (not (zero? (count %)))]}
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
  {:pre [(user-exists? username)]}
   (let [conn (d/connect uri)
         pwdhash (ffirst (q '[:find ?pwdhash :where 
                              [?id :user/username ?username]
                              [?id :user/password ?pwdhash]
                              :in ?username $]
                            username (db conn)))]
           (password-ok? pwd pwdhash)))

;;(registrer-user! "linus" "hahahaha" "hahahaha")
;;(valid-credentials? "linus" "hahahaha")
;;(valid-credentials? "amundsen" "polarisar")

(defn remove-user! [username]
  ;;retract in a transaction
)

(defn timestamp! [] (java.util.Date.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user login / session handling

;; will be based on 
;; https://github.com/hozumi/datomic-session

(defn logged-in? 
  "is there a cookie for this username in the db?"
  [conn ^String username]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(defn username->id 
  "finds the id of a user datom"
  [conn ^String username]
  (ffirst (q '[:find ?id :where [?id :user/username ?username] :in $ ?username] (db conn) username)))

(fact (username->id conn "kajsa") =not=> nil)

(defn verify-session [conn username cookie]
  (q '[:find ?id :where [?uid :user/username ?username] [?id :session/user ?uid] :in $ ?username] (db conn) username))

(fact (empty? (logged-in? conn "kajsa")) => falsey) ;;returns a hashset with an id of the cookie if ok.

(fact "that there now is a session cookie availiable"
 (q '[:find ?cookie :where [?cookie :session/cookie "a2342hahah"]] (db conn)) =not=> nil)

(fact "that there are users in the database and we can find them"
      (list-of-users) =not=> empty?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; answering

(defn valid-answer? 
  "constructs a set of all the ids and see if answer-id is in it"
  [answers answer-id]
  (not (nil? ((set (map :id answers)) answer-id))))

(deftest test-valid-answer?
  (is (valid-answer? [{:id 1}] 1))
  (is (not (valid-answer? [{:id 1} {:id 2}] 3))))

(defn answer! 
  "attempts to answer a given exercise from incoming request somehow"
  [student answer-id])

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

(defmulti render-task-to-html :task/type)

(defmethod render-task-to-html :task.type/yesno [task-datom] 
  (html [:html 
         [:head 
          [:title "yesno"]]
         [:body 
          [:h2 (:task/query task-datom)]
          [:div "yes"] 
          [:div "no"]]]))

(comment
  (render-task-to-html {:task/type :task.type/yesno
                        :task/query "is the sky blue?"}))

(defn read-answer-text [fourfield-answer-datom]
  (:task.fourfield.answer/text (d/entity (db conn) (:db/id fourfield-answer-datom))))

;;reads a fourfield and it's datom answers...
(defmethod render-task-to-html :task.type/fourfield [datom]
  (let [query-txt (:task/query datom)
        [a b c d]  (vec (map read-answer-text (shuffle (take 4 (:task.fourfield/answer datom)))))]
    (four-field-query-to-html query-txt a b c d)))

(comment (render-task-to-html {:task/type :task.type/fourfield
                               :task/query "what is 2+02?"}))

(defmethod render-task-to-html :task.type/freeform [datom]
  (html [:html [:head [:title "freeform"]]
         [:body [:h2 (:task/query datom)]
          [:div "_______________________ [OK]"]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; score aggregation / select new task
;; selected from the (ref-)links from the tasks/answers. 

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; task history / state

;; state is contained in datomic
;; history in the versioning.
;; later on we can do better analysis of the tasks.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; web templating

(defn about-page []
  (html [:html [:head [:title "About mekanikmaskinen"]]
         [:body
          [:h2 "about"]
          [:p "this is a super great project to teach you mechanics"]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; web request handling
(declare url-for)

(defn home-page [req]
  (ring-resp/content-type 
   (ring-resp/response "<html><h1>hello hello!</h1><a href=\"list\">List of tasks</a><br/>
<a href=\"/about\">about the site!</a></html>")
   "text/html"))

(defn about-page
  [request]
  (-> "<h2>about the site</h2> this is a site where you can learn anything by repeatedly answering the questions. you probably won't get feedback until you know everything you need to be knowing. progress is the key"
      ring-resp/response 
      (ring-resp/content-type "text/html")))

(defn login! [request]
  (ring-resp/response "login confirmation placeholder"))

(defn show-the-task 
  "just a way to show of that we can load one single datom and present some attribute of it according to it's :task/type."
  [req]
  (let [id (Long/parseLong (get-in req [:path-params :id]))
        entity (d/entity (db conn) id)]
    (do (log/info "found: " id  " of type: " (type id))
        (-> (render-task-to-html entity)
            ring-resp/response
            (ring-resp/content-type "text/html")))))

;;why does url-for escape the links??
(defn list-tasks 
  "very terrible function for list tasks, but it works"
[req]
  (let [ids      (q '[:find ?eid :where [?eid :task/type _]] (db conn))
        entities (map #(d/entity (db conn) (first  %)) ids)]
    (-> (apply str (map #(str "<a href=" (url-for :mekanikmaskin.service/show-the-task :app-name :mekanikmaskinen :params {:id (:db/id %)}) ">" (str (:db/id %)) "</a> " (:task/type %) " <br/>" ) entities))
        ring-resp/response
        (ring-resp/content-type "text/html"))))

(definterceptor session-interceptor
  (middlewares/session {:store (cookie/cookie-store)}))

(defroutes routes
  [[:mekanikmaskinen
    ["/" {:get home-page}
     ["/list" {:get list-tasks}]
     ["/task/:id" {:get show-the-task}]
     ["/about" {:get about-page}]]
;;     ["/login" ^:interceptors [middlewares/params middlewares/keyword-params session-interceptor] {:get login-page :post login!}]
   ]])

(def url-for (route/url-for-routes routes :app-name :mekanikmaskinen))

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

(defn gen-task 
  "example of generating task function"
  [{:keys [a b]}]
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
  "when running without argument it initializes the repl-loop, 
then (answer 24) answers the number 24"
  ([] (println (<!! result-chan) " : " (<!! query-chan)))
  ([what]
     (do (>!! answer-chan (str what)) (answer))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; testing if something is likely to be a datom
 
;; (defn decent-datom?
;;   "testing that something seems to be a decent datom, catching it early on"
;;   {:test [(decent-datom? {:db/id (d/tempid :db.part/user )})]}
;;   [datom]
;;   (and 
;;    (map? datom)
;;    (= (type (:db/id datom)) datomic.db.DbId)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; task creation

(defn find-duplicate-fourfield [task-text]
  (q '[:find ?eid :in $ ?text :where [?eid :task/query ?text] [?eid :task/type :task.type/fourfield] ] (db conn) task-text ))

(defn fourfield-saver 
  "expects the source (string) a text query, the correct answer and at least three non correct answers."
  [source task-text correct-answer-text & other-answers]
  {:pre [(>= (count other-answers) 3) (empty? (find-duplicate-fourfield task-text))]
   :post [(vector? %) (every? map? %) (every? :db/id %)]}
  (let [task-id           (d/tempid :db.part/user)
        correct-answer-id (d/tempid :db.part/user)
        other-answers-ids (take (count other-answers) (repeatedly #(d/tempid :db.part/user)))]

    (-> (mapv #(hash-map :db/id %1 :task.fourfield.answer/text %2) other-answers-ids other-answers)
        (conj 
         {:db/id task-id
          :task/type :task.type/fourfield
          :task/query task-text 
          :task/source source
          :task.fourfield/answer (vec (conj other-answers-ids correct-answer-id))})
        (conj 
         {:db/id correct-answer-id
          :task.fourfield.answer/text correct-answer-text
          :task.fourfield.answer/correct true})
)))

;; adding pi query, nuclear query
;; this could be read form a csv form is we are really lazy
;; with row references? moahaha

@(d/transact conn (fourfield-saver "public domain" "what's pi?"
                                   "~3.14"
                                   "~0.707"
                                   "~7.26"
                                   "~1.44"))

@(d/transact conn (fourfield-saver "?" "When an uranium nucleus undergoes fission, the energy released is primarily in the form of" 
                                   "kinetic energy of ejected neutrons"
                                   "gamma radiation" 
                                   "kinetic energy of fission fragments" 
                                   "an about equal mix of gamma radiation, kinetic energy of ejected neutrons and fission fragments" ))

(def some-fourfields  [
["LE" 
 "what is 10*35?" 
 "350" 
 "100" "35" "1035" "3500"]
["LE" 
 "what is 45+23" 
 "68" 
 "78" "58" "57"]
["ask.ca/~pywell/p121/Concept/Con02.html" 
 "An alien space traveller exploring the earth records that his phasor pistol, dropped from a high cliff, fell a distance of 1 glong in a time of 1 tock. How far will it fall in 2 tocks? (Ignore air resistance.)" 
 "4 glongs" 
 "3 glongs" "2 glongs" "1.5 glongs"]
["http://physics.usask.ca/~pywell/p121/Concept/Con37.html" 
 "A vector A is added to a vector B. Under what conditions does the resultant vector A + B have greatest magnitude?" 
 "When A and B are parallel and in the same direction." 
 "When A and B are parallel and in the opposite direction."
 "When A and B are perpendicular."  
 "The magnitude of A + B does not depend on the directions of A and B."]
["http://physics.usask.ca/~pywell/p121/Concept/Con37.html"
 "In a Young's double slit experiment, light and dark fringes are observed on distant screen. What would happen if the wavelength of the light was increased?" 
 "The distance between light fringes would increase."
 "The fringes would become brighter"
 "More fringes would be visible."
 "The distance between dark fringes would decrease."]
])

(map #(deref (d/transact conn (apply fourfield-saver %))) some-fourfields)
(log/info "inserted some four fields")

(defn lession 
  "should spit out a suitable datomic transaction to store this defined lession"
[name & forms]
  [name forms]
)




