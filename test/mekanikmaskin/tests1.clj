(ns mekanikmaskin.tests1
"bara för att få lite ordning"
  (:use [clojure.test]))

;;när man svarar på en uppgift, ett svar är ett objekt förstås
(def an-answer {:uppgift 124 :svar 2353 :timestamp 1330 :text "what is 1.00+0.02?"}) ;;en uppbeefad relation

;;är relationer något mer än så?

(defn get-task [id] {:svar 2353 :taskid 124})
(defn get-svar [id] {:text "1.02"})

;;intressant då:

;;är answer correct?

(defn correct? [answer]
  (let [task (get-task (:uppgift answer))]
    (= (:svar task) (:svar answer))))

(is (correct? an-answer)) ;;true

;; så vad händer då?

;;enklaste historian är en vektor

;; det går att göra jeopardyläge också... vad är en fråga på svaret?

(def story [(get-task 124) an-answer])

;;fast vänta här!

;;faktum är att get-tasket där är faktiskt en event likafullt!

{:type :show-task
 :task 124
 :timestamp 1327}

(def types #{:show-task :answer-task :show-lecture :login :logout})







