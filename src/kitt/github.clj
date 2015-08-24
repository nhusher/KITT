(ns kitt.github
  (:require [clj-http.client :as client]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]
            [kitt.utils :as u ]))

(timbre/refer-timbre)

(defn repo-description [repo]
  (try
    (-> (str "https://api.github.com/repos/" (env :github-org) "/" repo)
        (client/get {:accept  :json
                     :as      :json
                     :headers {"Authorization" (str "Bearer " (env :github-token))}})
        :body
        :description)
    (catch Exception e
      nil)))

(defn get-critical-issues []
  (try
    (-> (str "https://api.github.com/orgs/" (env :github-org) "/issues")
        (client/get {:accept       :json
                     :as           :json
                     :query-params {"labels" (env :github-danger-label)
                                    "filter" "all"
                                    "state"  "open"}
                     :headers      {"Authorization" (str "Bearer " (env :github-token))}})
        :body)
    (catch Exception e
      (timbre/info e)
      nil)))


(defn critical-issues []
  (->> (get-critical-issues)
       (filter (complement u/muted?))))