(ns kitt.chat-socket
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre ]
            [clj-http.client :as client]
            [clojure.core.async :as async :refer [>! <!]]
            [gniazdo.core :as ws]
            [cheshire.core :as cheshire]))

(timbre/refer-timbre)

(defrecord ChatSocket [url send-ch recv-ch]
  component/Lifecycle
  (start [this]
    (timbre/info "Opening slack websocket")
    (let [sock (ws/connect url
                           :on-receive (fn [s]
                                         (let [v (cheshire/parse-string s true)]
                                           (timbre/info "RECV" v)
                                           (async/put! recv-ch v)))
                           :on-error (fn [v] (timbre/error v))
                           :on-close (fn [code reason]
                                       (timbre/info "CLOSED" code reason)
                                       (async/close! send-ch)
                                       (async/close! recv-ch)))]
      (async/go
        (loop []
          (when-let [v (<! send-ch)]
            (timbre/info "SEND" v)
            (ws/send-msg sock (if (string? v) v (cheshire/generate-string v)))
            (recur)))
        (ws/close sock)
        (timbre/info "Slack socket closed"))
      this))
  (stop [ this ]
    (timbre/info "Closing slack socket")
    (async/close! send-ch)
    (async/close! recv-ch)
    this))


(defn make-chat-socket [url]
  (map->ChatSocket {:url url
                    :send-ch (async/chan 10)
                    :recv-ch (async/chan (async/dropping-buffer 10))}))

(defn make-message
  ([channel message]
   {:id      (rand-int 10000)
    :type    "message"
    :channel channel
    :text    message}))

(defn send-message! [ socket channel message ]
  (let [send-ch (:send-ch socket)]
    (async/go (>! send-ch (make-message channel message)))))
