(ns mekanikmaskin.studentstate
"functions for
adding old events to history
set the new task-at-hand 
and correct-answers and wrong-answers
likely set login-state as well"
 (:use clojure.test
       mekanikmaskin.task
       mekanikmaskin.utils
       midje.sweet))



(def students  {"pelle" {:task-at-hand {:exercise "what is 10+10?"
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
                         :history []}})

(def students-ref (ref students))

(defn task-at-hand [student-name]
  (get-in  @students-ref [student-name :task-at-hand]))

(deftest task-at-hand-test
  (is
   (= 
    (vec (keys (task-at-hand "pelle"))) 
    [:continuation :exercise-id :exercise :answers])))

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
                           :task task}
         ]
     ;;remove task
     (alter students-ref assoc-in [student :task-at-hand] nil)
     ;;add-it-to history with result and a timestamp!
     (alter students-ref update-in [student :history] conj new-history-item))))

(defn answer! 
  "attempts to answer a given exercise from incoming request somehow"
  [student answer-id])

;;why can't each individual student be a ref?
;;bcs they log in and out...

(deftest "task->history"
  (task->history "pelle" "answerid 111111"))

(defn get-new-task [])
(defn insert-new-task [])

;; can this really be?
;; status would be good - implicit or explicit?
(unfinished state)

;;how do pedestal handle this?
