(ns ring.websocket.transit-test
  (:require [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit]
            [ring.websocket.transit :as wst]
            [ring.websocket.protocols :as wsp]))

(defn- ->transit [x]
  (let [out (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) x)
    (.toString out "UTF-8")))

(deftest wrap-websocket-transit-test
  (let [log      (atom [])
        socket   (reify wsp/Socket
                   (-send [_ mesg]
                     (swap! log conj [:sock/message mesg])))
        handler  (wst/wrap-websocket-transit
                  (fn [_]
                    {:ring.websocket/listener
                     (reify wsp/Listener
                       (on-open [_ sock]
                         (wsp/-send sock {:x 1}))
                       (on-message [_ _ mesg]
                         (swap! log conj [:listener/message mesg]))
                       (on-close [_ _ code reason]
                         (swap! log conj [:listener/close code reason])))}))
        listener (:ring.websocket/listener (handler {}))]
    (wsp/on-open listener socket)
    (wsp/on-message listener socket (->transit {:y 2}))
    (wsp/on-close listener socket 1000 "Normal Closure")
    (is (= [[:sock/message (->transit {:x 1})]
            [:listener/message {:y 2}]
            [:listener/close 1000 "Normal Closure"]]
           @log))))
