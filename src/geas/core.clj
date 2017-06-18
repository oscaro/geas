(ns geas.core
  (:require [clojure.core.async :as as]
            [metrics
             [timers :as tmr]
             [meters :as met]]))

(defn ms
  [v]
  (when v (/ v 1000000.0)))

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

(def metric-path
  (memoize
   (fn [f metric]
     (mapv f (if (or (string? metric)
                     (keyword? metric))
               [metric]
               metric)))))

(defn mk-tracker-xform
  [metric registry perf-chan]
  (let [timer (start-timer metric registry)]
    (map
     (fn [x]
       (try
         (if (instance? Exception x)
           (let [latency (tmr/stop timer)
                 mpath (metric-path keyword metric)
                 mname (metric-path name metric)
                 data (-> {}
                          (assoc-in (conj mpath :call-duration) (ms latency))
                          (assoc-in (conj mpath :success) false))]
             (met/mark! (mk-meter (conj mname "failure") registry ))
             (as/put! perf-chan data)
             x)
           (let [latency (tmr/stop timer)
                 mpath (metric-path keyword metric)
                 mname (metric-path name metric)
                 data (-> {}
                          (assoc-in (conj mpath :call-duration) (ms latency))
                          (assoc-in (conj mpath :success) true))]
             (met/mark! (mk-meter (conj mname "success") registry))
             (as/put! perf-chan data)
             x))
         (catch Exception e
           (println "Error tracking perf with geas: " e)
           x))))))

(defn promise-chan
  ([{:keys [metric registry perf-chan] :or {metric "default"}}
    xform ex-handler]
   (let [perf-atom (atom nil)
         latency-xform (mk-tracker-xform metric registry perf-chan)
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
