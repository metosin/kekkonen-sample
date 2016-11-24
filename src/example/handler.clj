(ns example.handler
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]

            [plumbing.core :refer [defnk]]
            [example.security :as security]
            [schema.core :as s]))

;;
;; api
;;

(defn api [options]
  (ka/api
    (kc/deep-merge-map-like
      {:core {:type-resolver (k/type-resolver :command :query :effect)}
       :swagger {:data {:info {:title "Kekkonen CQRS API"}}}
       :ring {:types {:query {:methods #{:get}
                              :parameters {[:data] [:request :query-params]}}
                      :command {:methods #{:post}
                                :parameters {[:data] [:request :body-params]}}}}}
      options)))

(def effect-interceptor
  {:enter (fn [ctx] ctx)
   :leave (fn [ctx]
            (if (vector? (:response ctx))
              (reduce (fn [ctx [k effect]]
                        (k/invoke (k/get-dispatcher ctx) k (assoc ctx :data effect)))
                      ctx
                      (:response ctx))
              ctx))
   :error (fn [ctx error]
            (print error)
            ctx)})

;;
;; effect handlers
;; should return ctx
;;

(defnk ^:effect db
  [data :as ctx]
  (println "Updating db...")
  (swap! (:db ctx) data)
  ctx)

(defnk ^:effect email
  [[:data to :- [s/Str] type :- s/Keyword]
   db
   :as ctx]
  (let [message (case type
                  :registeration-email (let [{:keys [email id]} (last (:users @db))]
                                         (format "Welcome, %s, id: %s" email id)))]
    (println "Sending email -> " to ": " message))
  ctx)

(defnk ^:effect response
  [data :as ctx]
  (assoc ctx :response data))

;;
;; Business logic
;;

(defnk ^:query ping []
  {:ping "pong"})

(s/defschema User
  {:email s/Str})

(defnk ^:command register-user
  [data :- User]
  [[:effect/db (fn [db] (update db :users conj (assoc data :id (rand-int 10000))))]
   ; [:effect/email {:to [(:email data)]
   ;                 :message (format "Welcome, %s, id: %s" (:email data) "FIXME")}]
   [:effect/email {:type :registeration-email
                   :to [(:email data)]}]
   [:effect/response {:status 200 :body {:code :ok}}]])

;;
;; Application
;;

(defnk create [state]
  (api
    {:swagger {:ui "/"
               :spec "/swagger.json"
               :data {:info {:title "Example API"}}}
     :ring {:interceptors [security/api-key-authenticator
                           effect-interceptor]}
     :core {:handlers {:effect [#'db #'email #'response]
                       :api [#'register-user]}
            :meta {::roles security/require-roles}
            :context state}}))
