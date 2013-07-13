(ns mekanikmaskin.layouting
"make it possible to render a task-at-hand (that's in the session variable)"
(:use [hiccup.core]))

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

(defn textbox-query 
  "query? 
   [_answer__] [OK]"
  [query]
  (html [:html
         [:head [:title "textbox-query"]]
         [:body 
          [:h2 query]
          [:form { :action "/qbox" :method "POST"}
           [:input {:type "textbox"}]
           [:input {:type "submit" }]]]]))
