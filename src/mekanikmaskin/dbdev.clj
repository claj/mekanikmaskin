(ns mekanikmaskin.dbdev
  (:use [midje.sweet]
        [datomic.api :only (q db) :as d]))

;;really, what is best practice to handle a datomic db? how do we know if it exists or not already? what happends when it shuts down?


;;standard stuff, memory db
(def uri "datomic:mem://mekanikmaskin")

(d/create-database uri)
(def conn (d/connect uri))

(def schema-tx (read-string (slurp "resources/db/schema.dtm")))
@(d/transact conn schema-tx)

(def data-tx (read-string (slurp "resources/db/fauxusers.dtm")))
 @(d/transact conn data-tx)

(unfinished list-of-students)
(unfinished logged-in? ..student..)
(unfinished status? ..student..)

;;answer etc could go to datomic
;; need to wait for feedback from db before next page is shown though. callback-style


;;when a result is written, a listener should see that we need to do something new?
;;try out listener in datomic db
;;(add-listener fut f executor)

;;Register a completion listener for the future. The listener
;;will run once and only once, if and when the future's work is
;;complete. If the future has completed already, the listener will
;;run immediately.  Ordering of listeners is not guaranteed.

;;What future do one mean here? The @(transact?)
