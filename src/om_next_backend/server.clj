(ns om-next-backend.server
  (:require [clojure.java.io :as io]
            [ring.util.response :refer [response file-response resource-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [om-next-backend.middleware
             :refer [wrap-transit-body wrap-transit-response
                     wrap-transit-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [bidi.bidi :as bidi]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [om.next.server :as om]
            [om-next-backend.parser :as parser]))

(def routes
  ["" {"/"    :index
       "/api" {:get  {[""] :api}
               :post {[""] :api}}}])

(defn generate-response [data & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/transit+json"}
   :body    data})

(defn api [req]
  (generate-response
    ((om/parser {:read parser/readf :mutate parser/mutatef})
     {:conn (:datomic-connection req)} (:transit-params req))))

(defn handler [req]
  (let [match (bidi/match-route routes (:uri req)
                :request-method (:request-method req))]
    (case (:handler match)
      :api (api req)
      req)))

(defn wrap-connection [handler conn]
  (fn [req] (handler (assoc req :datomic-connection conn))))

(defn backend-handler [conn]
  (wrap-resource
    (wrap-transit-response
      (wrap-transit-params (wrap-connection handler conn)))
    "public"))

(defn backend-handler-dev [conn]
  (fn [req]
    ((backend-handler conn) req)))

(defrecord WebServer [port handler container datomic-connection]
  component/Lifecycle
  (start [component]
    (let [conn (:connection datomic-connection)]
      (if container
        (let [req-handler (handler conn)
              container   (run-jetty req-handler
                            {:port port :join? false})]
          (assoc component :container container))
        (assoc component :handler (handler conn)))))
  (stop [component]
    (.stop container)))

(defn dev-server [web-port]
  (WebServer. web-port backend-handler-dev true nil))

(defn prod-server []
  (WebServer. nil backend-handler false nil))
