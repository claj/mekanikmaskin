(ns mekanikmaskin.utils
  (:use [mekanikmaskin.config :only [min-password-length]])
  (:import  [org.jasypt.util.password StrongPasswordEncryptor]))

(defn timestamp! [] (java.util.Date.))

(defn encrypt-password 
  "encrypts password to hash"
  [pwd]
  {:post [(= (count %) 64)]
   :pre [(>= (count pwd) min-password-length)]}
  (.encryptPassword 
   (StrongPasswordEncryptor.) pwd))

(defn check-password [pwd pwdhash]
  (.checkPassword
   (StrongPasswordEncryptor.) pwd pwdhash))
