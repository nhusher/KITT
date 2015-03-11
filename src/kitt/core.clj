(ns kitt.core
  (:require [ clj-http.client    :as client ]
            [ gniazdo.core       :as ws ]
            [ clojure.core.async :as async :refer [ chan sliding-buffer dropping-buffer <! >! put! close! chan go
                                                   go-loop ]]
            [ cheshire.core      :as cheshire ]
            [ environ.core       :refer [ env ]]
            [ clojail.core       :refer [ sandbox safe-read ]]
            [ clojail.testers    :refer [ secure-tester ]]))

(defn make-message
  ([msg] (make-message msg (env :slack-default-channel)))
  ([msg channel]
   {:id      (rand-int 1000)
    :type    "message"
    :channel (or channel (env :slack-default-channel))
    :text    msg}))

(defn eval-sandboxed [ form-str ]
   (let [sb (sandbox secure-tester)
         writer (java.io.StringWriter.)
         result (try (sb (safe-read form-str) {#'*out* writer})
                     (catch Exception e
                       (.getMessage e)))]

     [ (str writer) result ]))

(defn make-socket
  ;; Clean up after the channels we make if we make them:
  ([ token in-ch ]
   (let [out-ch (chan (dropping-buffer 10))
         log-ch (chan (sliding-buffer 10))]
     (update-in
      (make-socket token in-ch (chan (dropping-buffer 10)) (chan (sliding-buffer 10)))
      [ :close! ]
      (fn [ old-close! ] (fn []
                           (old-close!)
                           (close! out-ch)
                           (close! log-ch))))))

  ([token in-ch out-ch log-ch]
   (let [slack-data (:body (client/post "https://slack.com/api/rtm.start"
                                        {:accept      :json
                                         :as          :json
                                         :form-params {:token token}}))
         ctrl-ch (chan)
         sock (ws/connect (:url slack-data)
                          :on-receive (fn [s]
                                        (let [v (cheshire/parse-string s true)]
                                          (put! log-ch [:recv v])
                                          (put! out-ch v)))
                          :on-error (fn [v]
                                      (put! log-ch [:error v]))
                          :on-close (fn []
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

     {:close-sock! (fn [] (close! ctrl-ch))
      :out-ch out-ch
      :log-ch log-ch })))


(defmulti parse-message
          (fn [ m ] (if (:subtype m)
                        [(keyword (:type m)) (keyword (:subtype m))]
                        [(keyword (:type m))])))

(defmethod parse-message :default [m]
  nil)


(defmethod parse-message [:message] [m]
  (if (re-matches #"(?s)^,\(.+" (:text m))
      (let [[out ret] (eval-sandboxed (clojure.string/replace (:text m) #"," ""))]
        (str (if out (str out "\n"))
             (if ret (str "`" (pr-str ret) "`"))))))

(defmethod parse-message [:message :message_changed] [m]
  (parse-message (:message m)))

(defmethod parse-message [:hello] [m]
  ":bender: I'm back, baby!")

(defn make-bot [ token ]
  (let [in-ch (chan)
        ctrl-ch (chan)
        {:keys [close-sock! out-ch log-ch ]} (make-socket token in-ch)]

    #_ (go-loop []
      (let [[v ch] (async/alts! [log-ch ctrl-ch])]
        (when-not (nil? v)
          (prn v)
          (recur))))

    (go-loop []
      (let [[v ch] (async/alts! [out-ch ctrl-ch])]
        (when-not (nil? v)
          (when-let [ r (parse-message v) ]
            (>! in-ch (make-message r (:channel v))))
          (recur))))

    (fn []
      (close-sock!)
      (close! ctrl-ch))))


(defonce bot (make-bot (env :slack-token)))

(defn reconnect! []
  (alter-var-root
   #'bot
   (fn [c]
     (if c (c))
     (make-bot (env :slack-token)))))
