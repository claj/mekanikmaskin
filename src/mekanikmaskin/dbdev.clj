(ns mekanikmaskin.dbdev
  (:use [midje.sweet]
        [ring.middleware.session.store]
        [mekanikmaskin.config :only [uri]]
        [datomic.api :only (q db) :as d]))

(defn list-of-users [conn]
  (q '[:find ?name :where [_ :user/username ?name]] (db conn)))

(defn conditional-connect-db 
  "if there is no database or given :force true
the database is removed and reinitialized, 
assert that there are some users availiable"
  [uri &{:keys [ force]}]
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

(def conn (conditional-connect-db uri))

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

;;should have a db function for logging in with session cookies et al
(unfinished login! ..username.. ..password..)

(unfinished status? ..student..)

;;answer etc could go to datomic
;; need to wait for feedback from db before next page is shown though. callback-style

;;when a result is written, a listener should see that we need to do something new?
;;try out listener in datomic db
;;(add-listener 
;; fut 
;;  f 
;; executor)
;;
;;Register a completion listener for the future. The listener
;;will run once and only once, if and when the future's work is
;;complete. If the future has completed already, the listener will
;;run immediately.  Ordering of listeners is not guaranteed.
;;the future comes from (transact/async)
