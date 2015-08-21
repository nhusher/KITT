(ns kitt.core
  (:require [com.stuartsierra.component :as component]
            [kitt.chat-bot :as bot]
            [kitt.chat-socket :as socket]
            [environ.core :refer [env]]))

(defn is-general? [ c ] (:is_general c))
(defn general-channel [ chat-info ] (->> (-> chat-info :channels) (filter is-general?) first :id))

(defn kitt-bot [token]
  (let [chat-info (socket/get-chat-info token)]
    (component/system-map
      :socket (socket/make-chat-socket (:url chat-info))
      :bot (component/using
             (bot/make-chat-bot
               (-> chat-info :self :id)
               (general-channel chat-info))
             [:socket]))))

(defonce bot (component/start (kitt-bot (env :slack-token))))

(defn stop! []
  (alter-var-root #'bot component/stop))

(defn start! []
  (alter-var-root #'bot (component/start (kitt-bot (env :slack-token)))))
