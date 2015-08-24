(ns kitt.utils
  (:require [ environ.core :refer [env]]))

(defn muted? [issue] (some #(= (:name %) (env :github-mute-label)) (:labels issue)))
(defn critical? [issue] (some #(= (:name %) (env :github-danger-label)) (:labels issue)))

(defn ellide-username [name]
  (apply str (first name) \u200B (rest name)))

(defn format-issue [i]
  (if (:assignee i)
    (str "*#" (:number i) ": " (:title i) "* assigned to " (ellide-username (-> i :assignee :login)) " " (:html_url i))
    (str "*#" (:number i) ": " (:title i) " is UNASSIGNED!* " (:html_url i))))


(defn is-general? [ c ] (:is_general c))
(defn chan-id-for-name [chat-info name] (->> (:channels chat-info) (filter #(= (:name %) name)) first :id))
(defn general-channel [ chat-info ] (->> (:channels chat-info) (filter is-general?) first :id))
