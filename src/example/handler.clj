(ns example.handler
  (:require [plumbing.core :refer [defnk]]
            [example.security :as security]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

(defnk ^:query ping []
  (success {:ping "pong"}))

(defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(defnk ^:query plus
  "playing with data"
  [[:data x :- s/Int, y :- s/Int]]
  (success (+ x y)))

(defnk ^:command inc!
  "a stateful counter"
  [counter]
  (success (swap! counter inc)))

(defnk ^:command reset-counter!
  "reset the counter. Just for admins."
  {::roles #{:admin}}
  [counter]
  (success (reset! counter 0)))

;;
;; Application
;;

(defnk create [state]
  (cqrs-api
   {:swagger {:ui "/"
              :spec "/swagger.json"
              :data {:info {:title "Example API"
                            :description
                            (str "This is a sample CQRS API with Kekkonen. You can find the source from "
                                 "[Github](https://github.com/metosin/kekkonen-sample).\n\n"
                                 "Try `kekkonen` as the api-key for more apis.")}
                     :externalDocs {:description "Find more about Kekkonen"
                                    :url "http://kekkonen.io"}}}
     :ring {:interceptors [security/api-key-authenticator]}
     :core {:handlers {:api {:pizza #'echo-pizza
                             :common [#'ping #'inc! #'plus #'security/get-user]
                             :admin [#'reset-counter!]}}
            :meta {::roles security/require-roles}
            :context state}}))
