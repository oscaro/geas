(ns geas.core
  (:require [clojure.core.async :as as]
            [metrics
             [timers :as tmr]
             [meters :as met]]))

(defn ms
  [v]
  (when v (/ v 1000000.0)))

(defn metric-name
  [metric nam]
  (if (string? metric)
    (str metric "-" nam)
    (conj (into [] metric) nam)))

(defn start-timer
  [metric registry]
  (tmr/start
   (if registry
     (tmr/timer registry metric)
     (tmr/timer metric))))

(defn mk-meter
  [metric registry]
  (if registry
    (met/meter registry metric)
    (met/meter metric)))

(defn mk-tracker-xform
  [metric registry perf-chan]
  (let [timer (start-timer metric registry)]
    (map
     (fn [x]
       (try
         (if (instance? Exception x)
           (do
             (met/mark! (mk-meter (metric-name metric "failure") registry ))
             (tmr/stop timer)
             x)
           (let [latency (tmr/stop timer)]
             (met/mark! (mk-meter (metric-name metric "success") registry))
             (as/put! perf-chan {:call-duration (ms latency)})
             x))
         (catch Exception e
           x))))))

(defn promise-chan
  ([{:keys [metric call-name registry perf-chan] :or {call-name "default"
                                                      metric    "default"}}
    xform ex-handler]
   (let [perf-atom (atom nil)
         latency-xform (mk-tracker-xform (metric-name metric call-name)
                                         registry perf-chan)
         comped (if xform
                  (comp latency-xform xform)
                  latency-xform)]
     (if ex-handler
       (as/promise-chan comped ex-handler)
       (as/promise-chan comped))))
  ([opts xform]
   (promise-chan opts xform nil))
  ([opts]
   (promise-chan opts nil nil)))
