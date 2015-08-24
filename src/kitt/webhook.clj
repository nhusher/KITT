(ns kitt.webhook
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [<! >!]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as timbre]
            [cheshire.core :as cheshire])
  (:import (org.eclipse.jetty.server Server)))

(timbre/refer-timbre)

(defn make-server [ch]
  (jetty/run-jetty
    (fn [req]
      (when-let [evt (keyword (get (:headers req) "x-github-event"))]
        (timbre/info "WEBHOOK" evt)
        (async/put! ch [evt (cheshire/parse-string (slurp (:body req)) true)]))
      {:status 200 :headers {"Content-Type" "text/plain"} :body "OK"})
    {:port (env :jetty-port) :join? false}))

(defrecord Webhook [^Server server internal-ch hook-pub]
  component/Lifecycle
  (start [this]
    (timbre/info "Starting up webhook server")
    (let [internal-ch (async/chan (async/sliding-buffer 10))
          server (make-server internal-ch)
          pub (async/pub internal-ch #(first %))]
      (assoc this :server server :internal-ch internal-ch :hook-pub pub)))
  (stop [this]
    (timbre/info "Shutting down webhook server")
    (.stop server)
    (timbre/info internal-ch)
    (async/close! internal-ch)
    this))

(defn make-webhook []
  (map->Webhook {}))

(defn listen [webhook topic]
  (let [ch (async/chan)]
    (async/sub (:hook-pub webhook) topic ch)
    ch))