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
  (println "ping")
  [[:effect/response {:ping "pong"}]])

(s/defschema User
  {:email s/Str})

(defnk ^:command register-user
  [data :- User]
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
  (println "Updating db:" data)
  (assoc ctx :after-db {:tx 112233
                        :new-user-id 123456}))

(defnk ^:effect email
  [data :- s/Keyword [:after-db new-user-id :- Long] :as ctx]
  (let [{:keys [to message]} (case data
                               :registeration-email
                               {:to "foo"
                                :message (format "Your new home http://foo.bar/user/%d" new-user-id)})]
    (println "Sending email ->" to ":" message))
  ctx)

(defnk ^:effect response
  [data :as ctx]
  (assoc ctx :response {:status 200 :body data}))

;;
;; ========================================================================
;; Effect handling
;; ========================================================================
;;

(def effect-interceptor
  {:enter (fn [ctx] ctx)
   :leave (fn [{response :response :as ctx}]
            (if (vector? response)
              (do (println "effects:" response)
                  (reduce (fn [ctx [k effect]]
                            (k/invoke (k/get-dispatcher ctx) k (assoc ctx :data effect)))
                          ctx
                          response))
              (do (println "no-fx" response)
                  ctx)))
   :error (fn [ctx error]
            (assoc ctx :kekkonen.interceptor/queue error))})


(defn make-sente-handler [dispatcher]
  (fn [{action :id :as sente-event}]
    (do (println "handling event:" action)
        (->> {:request sente-event}
             (k/dispatch dispatcher :invoke action)))))

(let [dispatcher (k/dispatcher {:type-resolver (k/type-resolver :command :query :effect)
                                :handlers {:effect [#'db #'email #'response]
                                           :api [#'register-user #'ping]}
                                :interceptors [security/api-key-authenticator
                                               effect-interceptor]})
      sente-handler (make-sente-handler dispatcher)]
  (sente-handler {:id :api/ping}))


(defn api [options]
  (ka/api
    (kc/deep-merge-map-like
      {:core {:type-resolver (k/type-resolver :command :query :effect)}
       :ring {:types {:query {:methods #{:get}
                              :parameters {[:data] [:request :query-params]}}
                      :command {:methods #{:post}
                                :parameters {[:data] [:request :body-params]}}}}}
      options)))

;;
;; ========================================================================
;; Application
;; ========================================================================
;;

(defnk create [state]
  (api
    {:swagger {:ui "/"
               :spec "/swagger.json"
               :data {:info {:title "Example API"}}}
     :core {:handlers {:effect [#'db #'email #'response]
                       :api [#'register-user #'ping]}
            :meta {::roles security/require-roles}
            :context state
            :interceptors [security/api-key-authenticator
                           effect-interceptor]}}))
