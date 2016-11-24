(ns example.system
  (:require [com.stuartsierra.component :as component]
            [palikka.components.http-kit :as http-kit]
            [example.handler :as handler]))

(defn new-system [config]
  (component/map->SystemMap
    {:state (reify component/Lifecycle
              (start [_] {:db (atom {:users []})}))
     :http (component/using
             (http-kit/create
               (:http config)
               {:fn
                (if (:dev-mode? config)
                  ; re-create handler on every request
                  (fn [system] #((handler/create-ring-handler system) %))
                  handler/create-ring-handler)})
             [:state])}))
