(ns kitt.chat-bot
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [clojure.core.async :as async :refer [>!]]
            [kitt.chat-socket :refer [send-message!]]
            [kitt.eval :refer [eval-sandboxed]]
            [kitt.github :as github]))

(timbre/refer-timbre)

(defn addressed-to [message] (second (re-find #"^<@([^>]+)>" (:text message))))
(defn message-content [message] (second (re-find #"^<@[^>]+>:?\s*(.+)" (:text message))))

(def befuddl ["Gabh mo leithscéal?" "I'm sorry, I don't understand." "I don't know."])
(defn befuddled [] (rand-nth befuddl))

(defn parse-message [message]
  (cond
    (re-matches #"^`\(.+\)\s*`$" message)
    (try
      (let [[out ret] (eval-sandboxed (second (re-find #"^`(\(.+\)\s*)`\s*$" message)))]
        (str (if out (str out "\n"))
             (if ret (str "`" (pr-str ret) "`"))))
      (catch Exception e
        (timbre/info (.getMessage e))
        (befuddled)))

    (re-matches #"[Ww]hat('s| is) ([^\?]+)\?*" message)
    (or (github/repo-description (last (re-find #"[Ww]hat('s| is) ([^\?]+)\?*" message)))
        (befuddled))

    (re-matches #"[Cc]ritical.*\?$" message)
    (github/critical-issues)

    (re-matches #"botsnack" message)
    ":pizza: Om nom nom nom."

    :else nil))

(defmulti generate-response
          (fn [_ message] (if (:subtype message)
                            [(keyword (:type message)) (keyword (:subtype message))]
                            [(keyword (:type message))])))

(defmethod generate-response :default [_ _]
  nil)

(defmethod generate-response [:message] [self-id message]
  (when (= (addressed-to message) self-id)
    (parse-message (message-content message))))

(defmethod generate-response [:message :message_changed] [self-id message]
  (when-let [r (generate-response self-id (:message message))]
    (str r " _(from edit)_")))

(defmethod generate-response [:hello] [_ _]
  ":bender: I'm back, baby!")

(defrecord ChatBot [self-id default-channel ctrl-ch]
  component/Lifecycle
  (start [this]
    (timbre/info "Starting chatbot" self-id)
    (let [ctrl-ch (async/chan)]
      (async/go-loop []
        (when-let [[v _] (async/alts! [(-> this :socket :recv-ch) ctrl-ch])]
          (when-let [response (generate-response self-id v)]
            (send-message! (:socket this) (or (:channel v) default-channel) response))
          (recur)))
      (assoc this :ctrl-ch ctrl-ch)))
  (stop [this]
    (timbre/info "Stopping chatbot")
    (async/close! ctrl-ch)
    this))


(defn make-chat-bot [self-id default-channel]
  (map->ChatBot {:self-id self-id :default-channel default-channel}))