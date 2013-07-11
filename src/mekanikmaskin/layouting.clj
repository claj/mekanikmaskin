(ns mekanikmaskin.layouting
"make it possible to render a task-at-hand (that's in the session variable)")

(defmulti layout-task :type)

(defmethod layout-task :four-field [task])


