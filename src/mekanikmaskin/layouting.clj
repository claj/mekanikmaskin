(ns mekanikmaskin.layouting
"make it possible to render a task-at-hand (that's in the session variable)"
(:use [hiccup.core])
)

(defn four-field 
  "extemely quick and dirty four field layouting function"
  [query answer-1 answer-2 answer-3 answer-4]
  (html 
   [:html 
    [:head [:title "four field"]]
    [:body
     [:h2 query]
     [:div {:style "border: solid; width: 10em;"} answer-1] " " [:div {:style "border: solid; width: 10em;"} answer-2] " "
     [:div {:style "border: solid; width: 10em;"} answer-3] " "
     [:div {:style "border: solid; width: 10em;"} answer-4]]]))

(spit "testfil.html" (four-field "vad &auml;r 2+2?" "1" "2" "3" "4"))

