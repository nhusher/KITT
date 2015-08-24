(ns kitt.core
  (:require [com.stuartsierra.component :as component]
            [kitt.chat-bot :as bot]
            [kitt.chat-socket :as socket]
            [kitt.slack :as slack]
            [environ.core :refer [env]]
            [kitt.utils :as u]))


(defn kitt-bot [token]
  (let [chat-info (slack/get-chat-info)]
    (component/system-map
      :socket (socket/make-chat-socket (:url chat-info))
      :bot (component/using
             (bot/make-chat-bot
               (-> chat-info :self :id)
               (u/general-channel chat-info))
             [:socket]))))

(defonce bot (component/start (kitt-bot (env :slack-token))))

(defn stop! []
  (alter-var-root #'bot component/stop))

(defn start! []
  (alter-var-root #'bot (component/start (kitt-bot (env :slack-bot-token)))))
