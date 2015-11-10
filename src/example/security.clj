(ns example.security
  (:require [kekkonen.cqrs :refer :all]
            [plumbing.core :refer [defnk]]
            [clojure.set :as set]
            [schema.core :as s]))

(s/defschema User
  {:name s/Str
   :roles #{s/Keyword}})

(def ^:private +users+
  {"kekkonen" {:name "Urho Kaleva Kekkonen" :roles #{:admin}}})

(defn api-key-authenticator [context]
  (let [api-key (-> context :request :query-params :api_key)]
    (assoc context :user (+users+ api-key))))

(defn require-roles [required]
  (fn [context]
    (let [roles (-> context :user :roles)]
      (if (seq (set/intersection roles required))
        context))))

(defnk ^:query get-user
  {:responses {:default {:schema (s/maybe User)}}}
  [user] (success user))
