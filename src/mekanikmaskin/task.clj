(ns mekanikmaskin.task
"task is the various exercises to be solved by students, 
hopefully to learn something
about the course(s) at hand

the individual task can only be solved once.

the 
"
  (:use [midje.sweet])
  (:import [clojure.lang Keyword])
)

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

(defn task-bindings-gen
  "returns a random task binding
doesn't work as expected?"
 []
 {:post [(map? %), (number? (:a %)), (number? (:b %))]}
  (io!
   {:a (+ (rand-int 10) 10)
    :b (+ (rand-int 10) 10)}))

(with-bindings (task-bindings-gen)
  (str a " , " b))

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

(ns-unmap *ns* 'taskreader)

(tr :a)
(tr :b)


(taskreader :smallint)

(taskreader :smallint)

(defmulti sokrates (fn [x] [(type x) x])
(defmethod sokrates [Keyword :hobes ] [x] "hobes")
(defmethod sokrates [Keyword :kalas] [x] "kalas")

(sokrates :hobes)
(sokrates :kalas)

;; seems to be my own reader...
  
;;there is the possibility to adress the variables by
;;the order


;; the state enough verbose to be useful is

[[:text "what is " [:latex [:var :named-a :smallint] " + " [:var :named-b :smallint]] "?"]

;; how true to mathematical representation should the model be?

;; this is very similar to html, maybe?
;; the latex gets rendered as an image, and should be stored somewhere as such
;; the answers should be registrered somewhere for the user

;; 

;; answers must be dedicated to a certain task
;; a task must be dedicated to one certain session
;;the task coupled to a session is changing over time. maybe it's just an update of a certain position in a session datom?

;,or the sessions are actually differed - many persons can work on one task at a certain time. if they are working togheter - a session could therefore be a "work" as well.
