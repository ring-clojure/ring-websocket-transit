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
  (testing "sending and receiving messages"
    (let [log      (atom [])
          socket   (reify wsp/Socket
                     (-send [_ mesg]
                       (swap! log conj [:socket/message mesg])))
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
      (is (= [[:socket/message (->transit {:x 1})]
              [:listener/message {:y 2}]
              [:listener/close 1000 "Normal Closure"]]
             @log))))
  (testing "socket forwards methods"
    (let [log      (atom [])
          socket   (reify wsp/Socket
                     (-open? [_] (swap! log conj [:socket/open?]))
                     (-ping [_ data] (swap! log conj [:socket/ping data]))
                     (-pong [_ data] (swap! log conj [:socket/pong data]))
                     (-close [_ c r] (swap! log conj [:socket/close c r])))
          handler  (wst/wrap-websocket-transit
                    (fn [_]
                      {:ring.websocket/listener
                       (reify wsp/Listener
                         (on-open [_ sock]
                           (wsp/-open? sock)
                           (wsp/-ping sock :xxx)
                           (wsp/-pong sock :yyy)
                           (wsp/-close sock 1000 "close")))}))
          listener (:ring.websocket/listener (handler {}))]
      (wsp/on-open listener socket)
      (is (= [[:socket/open?]
              [:socket/ping :xxx]
              [:socket/pong :yyy]
              [:socket/close 1000 "close"]]
             @log))))
  (testing "listener forwards methods"
    (let [log      (atom [])
          socket   (reify wsp/Socket)
          handler  (wst/wrap-websocket-transit
                    (fn [_]
                      {:ring.websocket/listener
                       (reify wsp/Listener
                         (on-pong [_ _ data]
                           (swap! log conj [:listener/pong data]))
                         (on-error [_ _ ex]
                           (swap! log conj [:listener/error ex])))}))
          listener (:ring.websocket/listener (handler {}))]
      (wsp/on-pong listener socket :xxx)
      (wsp/on-error listener socket :eee)
      (is (= [[:listener/pong :xxx]
              [:listener/error :eee]]
             @log)))))
