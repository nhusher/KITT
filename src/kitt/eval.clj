(ns kitt.eval
  (:require [clojail.core :refer [sandbox safe-read]]
            [clojail.testers :refer [secure-tester]])
  (:import (java.io StringWriter)))


(defn eval-sandboxed [form-str]
  (let [sb (sandbox secure-tester)
        writer (StringWriter.)
        result (sb (safe-read form-str) {#'*out* writer})]
    [(str writer) result]))
