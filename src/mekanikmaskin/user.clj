(ns mekanikmaskin.user
  (:use [datomic.api         :only [db q] :as d]
        [midje.sweet]
        [mekanikmaskin.utils :only [encrypt-password check-password]])
  (:require [mekanikmaskin.config :as config]))

(defn user-exists?
  "returns the hash-set of the user"
  [username]
  {:post [(<= (count %) 1)]}
  (let [conn (d/connect config/uri)]
    (q '[:find ?id 
         :where [?id :user/username ?username] 
         :in ?username, $] 
       username (db conn))))

(defn registrer-user! [username pwd pwd2]
  {:pre [(= pwd pwd2), (empty? (user-exists? username))]
   :post [(user-exists? username)]}
  (let [conn (d/connect config/uri)]
    @(d/transact conn [{:db/id (d/tempid :db.part/user)
                        :user/username username
                        :user/password (encrypt-password pwd)}])))

(defn valid-credentials? [username pwd]
  {:pre [user-exists? username]}
   (let [conn (d/connect config/uri)
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
 
