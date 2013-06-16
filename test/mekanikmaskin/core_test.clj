(ns mekanikmaskin.core-test
  (:use [clojure.test]
        [mekanikmaskin.core]
        [mekanikmaskin.task]
        [midje.sweet])
  (:require [mekanikmaskin.studentstate :as studentstate]
            [mekanikmaskin.utils :as utils]))

;;utilities
(fact (= (class (utils/timestamp!)) java.util.Date))

;;# domain objects

;;## user (student)
(unfinished legal-state?)
(fact (studentstate/state ....student...) => legal-state?)

;;sign up
(unfinished register!)
(future-fact "validate incoming registering data")
(future-fact "test the database storage of the new user")

;;login
(unfinished login!)
(future-fact "handle errorneous login attempts")

;;logout
(unfinished logout!)
(future-fact "dont drop the other student logged in")

;;get new task
(unfinished assign-new-task)

;;solve/answer task
(unfinished answer! ..student.. ..ans-id..)

;;gain knowledge badge
(unfinished add-knowledge-badge! ..student.. ..knowledge...)

;;sign up for new goals
(unfinished add-goal!)
(unfinished remove-goal!)

;;group up with someone else (also logged in)
(unfinished team-up-with!)
(unfinished availiable-to-team-up-with)

;;buzz togheter about a task
(unfinished start-chat-with)

;;get statistics over ones progressions
(unfinished statistics-report)
(future-fact "what happends with the currect task if you head somewhere else? - fail? pause?")

;;## task

;;selected given user state

;;wrongly answered

;;correctly answered

;;attempted to be cheated with

;;rendered

;;## goal

;; reached by a user (given user state)

;; specified (by a teacher)


;;## teacher

;;everything as a user can do

;;invite other users

;;overview of students/statistics

;;chat/reply to questions

;;define a new course
(unfinished add-course! ..course.. ..knowledge..)

;;define a new task
(unfinished add-task! ..description.. ..knowledge.. ..level..)

;;## administative tasks and events

;;setup up new environment
(unfinished initialize-db! ..system-configuration..)

;;run the process

;;initialize a db
(unfinished create-new-db)
(future-fact "make sure we don't easily overwrite already saved material")
(unfinished initialize-current-db)

;;start a webserver (port etc)
(unfinished start-new-webserver)

;;visit the webpage
(future-fact "visit the first page to make sure it's up at all")
(future-fact "login with a test-user to test login functionality")

;;invite new users to sign up
(unfinished invite)
(future-fact "make sure invites are stored so we don't spam people uness")

;;connect to CAS login service

;;## task selector refinement


;;##logback logging
;;paralell and even more detailed than datomic logging...

;;browse some students progression

;;identify errors in answers

;;recreate some students answer if the task was wrongly constructed

;;handle cheating attempts

(unfinished cheating-alarm!)

;;report the chi2-results etc
   ;;future fact!


