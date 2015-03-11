(ns kitt.core
  (:require [ clj-http.client    :as client ]
            [ gniazdo.core       :as ws ]
            [ clojure.core.async :as async :refer [ chan dropping-buffer <! >! put! close! chan go go-loop ]]
            [ cheshire.core      :as cheshire ]
            [ environ.core       :refer [env]]))


(defn make-socket!
  ([ token in-ch ]
   (make-socket! token in-ch (chan 10) (chan 10)))

  ([token in-ch out-ch log-ch]
   (let [slack-data (:body (client/post "https://slack.com/api/rtm.start"
                                        {:accept      :json
                                         :as          :json
                                         :form-params {:token token}}))
         ctrl-ch (chan)
         sock (ws/connect (:url slack-data)
                       :on-receive (fn [s]
                                     (let [ v (cheshire/parse-string s true) ]
                                       (put! log-ch [:recv v])
                                       (put! out-ch v)))
                       :on-error   (fn [v]
                                     (put! log-ch [:error v]))
                       :on-close   (fn []
                                     (put! log-ch [:closed])
                                     (close! ctrl-ch)))]

     (go-loop []
       (let [[v _] (async/alts! [ctrl-ch in-ch])
             s (if (string? v) v (cheshire/generate-string v))]
         (if (nil? v) (ws/close sock)
             (do
               (put! log-ch [:send v])
               (ws/send-msg sock s)
               (recur)))))

     {:close! (fn [] (close! ctrl-ch))
      :out-ch out-ch
      :log-ch log-ch })))

(def in-ch (chan))
(def close-sock! nil)

(defn reconnect! []
  (alter-var-root #'close-sock!
                  (fn [ c ]
                    (if c (c))
                    (let [{ :keys [ close! out-ch log-ch ]} (make-socket! (env :slack-token) in-ch)]
                      (go-loop []
                        (when-let [ m (<! out-ch)]
                          (prn m)
                          (recur)))
                      close!))))


