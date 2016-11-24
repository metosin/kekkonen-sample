(ns example.handler
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [kekkonen.interceptor :as interceptor]

            [plumbing.core :refer [defnk]]
            [potpuri.core :as p]
            [example.security :as security]
            [schema.core :as s]))

;;
;; ========================================================================
;; Business logic
;; ========================================================================
;;

(defnk ^:query ping
  []
  (println "ping!")
  [[:effect/response {:ping "pong"}]
   [:effect/response-message "pong"]])

(s/defschema User
  {:email s/Str})

(defnk ^:command register-user
  [data :- User]
  (println "register-user!" data)
  [[:effect/db [[:db/add -1 :user/email (:email data)]]]
   [:effect/email :registeration-email]
   [:effect/response {:code :ok}]])

;;
;; ========================================================================
;; Effect handlers
;; ========================================================================
;;

(defnk ^:effect db
  [data :as ctx]
  (println "effect: updating db:" data)
  (assoc ctx :after-db {:tx 112233
                        :new-user-id 123456}))

(defnk ^:effect email
  [data :- s/Keyword
   [:after-db new-user-id :- Long]
   :as ctx]
  (println "effect: sending email: template:" data "user-id:" new-user-id)
  ctx)

(defnk ^:effect response
  [data :as ctx]
  (println "effect: response" data)
  (assoc ctx :response {:status 200 :body data}))

(defnk ^:effect response-message
  [data [:sente id send-fn] :as ctx]
  (println "effect: response-message" data send-fn)
  (send-fn [id data])
  ctx)

;;
;; ========================================================================
;; Effect handling
;; ========================================================================
;;

(def effect-interceptor
  {:leave (fn [{:keys [response effects-dispatcher] :as ctx}]
            (if (vector? response)
              (reduce (fn [ctx [effect-id effect-data]]
                        (if (-> effects-dispatcher :handlers effect-id)
                          (k/invoke effects-dispatcher effect-id (assoc ctx :data effect-data))
                          ctx))
                      ctx
                      response)
              ctx))
   :error (fn [ctx error]
            (println "effect-interceptor: error" error)
            (assoc ctx :kekkonen.interceptor/queue error))})

;;
;; ========================================================================
;; Application
;; ========================================================================
;;

(defn make-effects-dispatcher [handlers]
  (k/dispatcher {:type-resolver (k/type-resolver :effect)
                 :handlers (select-keys handlers [:effect])}))

(defn concatv [& v]
  (vec (apply concat v)))

(defn make-kekkonen-config [state common-handlers & context-effect-handlers]
  (let [handlers (update common-handlers :effect concatv context-effect-handlers)]
    (println "=>" handlers)
    {:handlers (dissoc handlers :effect)
     :type-resolver (k/type-resolver :command :query)
     :meta {::roles security/require-roles}
     :context (assoc state :effects-dispatcher (make-effects-dispatcher handlers))
     :interceptors [security/api-key-authenticator
                    effect-interceptor]}))

;;
;; ========================================================================
;; Ring handler:
;; ========================================================================
;;

(def handlers {:effect [#'db #'email]
               :api [#'register-user #'ping]})

(defnk create-ring-handler [state]
  (ka/api
    {:core (make-kekkonen-config state handlers #'response)
     :swagger {:ui "/"
               :spec "/swagger.json"
               :data {:info {:title "Example API"}}}
     :ring {:types {:query {:methods #{:get}
                            :parameters {[:data] [:request :query-params]}}
                    :command {:methods #{:post}
                              :parameters {[:data] [:request :body-params]}}}}}))

;;
;; ========================================================================
;; Sente handler:
;; ========================================================================
;;

(defn sente-event->ctx [sente-event]
  {:data (-> sente-event :event second)
   :sente sente-event})

(defn create-sente-handler [state]
  (let [dispatcher (k/dispatcher (make-kekkonen-config state handlers #'response-message))]
    (fn [{action :id :as sente-event}]
      (k/invoke dispatcher action (sente-event->ctx sente-event)))))

(comment
  (let [sente-handler (create-sente-handler {})]
    (sente-handler {:id :api/ping
                    :event [nil nil]
                    :send-fn (fn [data] (println "sente: send response" data))}))

  (let [sente-handler (create-sente-handler {})]
    (sente-handler {:id :api/register-user
                    :event [nil {:email "baba"}]
                    :send-fn (fn [data] (println "sente: send response" data))})))
