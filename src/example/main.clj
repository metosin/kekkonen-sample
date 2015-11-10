(ns example.main
  (:require [reloaded.repl :refer [set-init! go]])
  (:gen-class))

(defn -main [& [port]]
  (let [port (or port 3000)]
    (require 'example.system)
    (set-init! #((resolve 'example.system/new-system) {:http {:port port}}))
    (go)))
