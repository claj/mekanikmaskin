# Introduction to mekanikmaskin

TODO: write [great documentation](http://jacobian.org/writing/great-documentation/what-to-write/)

# "Story" or interaction

    [{:type :login :user 819 :session-version 1 :session 12421414}
     {:type :show-task :task 123} {:type :answer :answer 462}
     {:type :selector :strategy :progression}
     {:type :show-task :task 125} {:type :answer :answer 13512}
     {:type :selector :strategy :progression}
     {:type :show-task :task 2031} {:type :extra-info :info 92} <waits for user interaction!> {:type :answer :answer 9922}
     {:type :selector :strategy :recover}
     {:type :show-task :task 32}  {:type :answer :answer 62}
     {:type :selector :strategy :progression}
     {:type :show-task :task 150}
     {:type :logut :user 819}]

This is an example of an interaction. The user logs in, there's a session-version since this format is bound to change.

Then the user get's the first task, 123, answers with the answer 462 to it (remeber no attributes or judgements here, we aren't perfect either). Depending on the answer or something else the algorithm that decides next task choses task 125.

The :strategy is given to know why we chose the next task over others. It's also important not to mix up "you might also like"-type of answers with the repetition-functionality (which jumps in with some occurence).

Through these strategy-things we can distinguish different more or less successful relations and even make a-priori tests etc to know what task are suitable to take after each other. And the model is expandable for further adjustments in task-selection and entirely new functionality (like joining two sessions with a linking statement).
