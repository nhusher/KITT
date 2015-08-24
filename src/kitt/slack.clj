(ns kitt.slack
  (:require [clj-http.client :as client]
            [environ.core :refer [env]]))


(defn get-chat-info []
  (:body (client/post "https://slack.com/api/rtm.start"
                      {:accept      :json
                       :as          :json
                       :form-params {:token (env :slack-token)}})))

