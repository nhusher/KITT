(ns kitt.bullhorn
  (:require [com.stuartsierra.component :as component]
            [kitt.github :as github]
            [clojure.core.async :as async :refer [>! <!]]
            [kitt.utils :as u]
            [kitt.chat-socket :as socket]
            [clj-time.core :as t]
            [clj-time.predicates :as pr]))

(defn get-critical-issues []
  (let [criticals (github/critical-issues)]
    (into {} (map (fn [i] [(:number i) i])) criticals)))

;; Logic:
;; If weekend or between 11pm EST and 5am EST - duration = 1/hr
;; If webhook issue labeling or creation comes in, wait 30 seconds and pull the issue to check if its been muted
;; If unassigned and unmuted log once/2m
;; If assigned and unmuted log once/duration

(def five-minutes (* 5 60 1000))
(def ten-minutes (* 10 60 1000))
(def twenty-minutes (* 20 60 1000))

(defn now-in-ny []
  (t/to-time-zone (t/now) (t/time-zone-for-id "America/New_York")))

(defn send-bullhorn-message [socket channel issues]
  (let [unassigned (filter #(false) issues)
        assigned (filter #(true) issues)
        message (map u/format-issue (concat unassigned assigned))]
    (when message (socket/send-message! socket channel message))))

(defn should-send? [last-sent critical-issues]
  (let [now (now-in-ny)
        interval (t/in-millis (t/interval last-sent now))
        is-weekend (pr/weekend? now)
        is-ungodly-hour (or (< (t/hour now) 5) (> (t/hour now) 11))
        issues-unassigned (some #(nil? (:assignee %)) critical-issues)]
    ;; Only send iff:
    (or issues-unassigned ; there are unassigned issues
        (and (not is-weekend) (not is-ungodly-hour) (> ten-minutes interval)) ; it's not a weekend and it's not the middle of the night (and ten minutes have past)
        (and is-weekend (not is-ungodly-hour) (> twenty-minutes interval))))) ; it is the weekend and it's not the middle of the night (and twenty minutes have past)
    ;; No messages in the middle of the night on weekends

(defrecord Bullhorn [bullhorn-channel-id ctrl-atom]
  component/Lifecycle
  (start [this]
    (let [last-sent (atom (t/local-date-time 1970 1 1))
          ctrl-atom (atom true)]
      (async/go-loop []
        (let [criticals (get-critical-issues)]
          (when (should-send? @last-sent criticals)
            (send-bullhorn-message (:socket this) bullhorn-channel-id criticals)
            (reset! last-sent (now-in-ny)))
          (<! (async/timeout five-minutes))
          (when @ctrl-atom (recur)))))
    (assoc this :ctrl-atom ctrl-atom))
  (stop [this]
    (reset! ctrl-atom false)
    this))