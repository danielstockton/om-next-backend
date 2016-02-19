(ns om-next-backend.core
  (:require [com.stuartsierra.component :as component]
            [om-next-backend.db :as db]
            [om-next-backend.server :as server]))

(defn dev-system [config-options]
  (let [{:keys [db-uri web-port]} config-options]
    (component/system-map
      :db (db/new-database db-uri)
      :webserver
      (component/using
        (server/dev-server web-port)
        {:datomic-connection :db}))))

(defn prod-system [config-options]
  (let [{:keys [db-uri]} config-options]
    (component/system-map
      :db (db/new-database db-uri)
      :webserver
      (component/using
        (server/prod-server)
        {:datomic-connection :db}))))

(def servlet-system (atom nil))

(defn dev-start []
  (let [sys  (dev-system
               {:db-uri   "datomic:mem://localhost:4334/db"
                :web-port 8081})
        sys' (component/start sys)]
    (reset! servlet-system sys')
    sys'))

(defn prod-start []
  (let [s (prod-system
            {:db-uri "datomic:mem://localhost:4334/db"})]
    (let [started-system (component/start s)]
      (reset! servlet-system started-system))))

(defn stop [])
