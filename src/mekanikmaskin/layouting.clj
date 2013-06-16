(ns mekanikmaskin.layouting)

(defmulti layout-task :type)

(defmethod layout-task :four-field [task])
