(ns ring.websocket.transit
  (:require [cognitect.transit :as transit]
            [ring.websocket.protocols :as wsp])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn- <-transit [^CharSequence s]
  (let [in (ByteArrayInputStream. (.getBytes (.toString s)))]
    (transit/read (transit/reader in :json))))

(defn- ->transit [x]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) x)
    (.toString out "UTF-8")))

(defn- wrap-socket [socket]
  (reify wsp/Socket
    (-open? [_]
      (wsp/-open? socket))
    (-send [_ message]
      (wsp/-send socket (->transit message)))
    (-ping [_ data]
      (wsp/-ping socket data))
    (-pong [_ data]
      (wsp/-pong socket data))
    (-close [_ code reason]
      (wsp/-close socket code reason))))

(defn- wrap-listener [listener]
  (reify wsp/Listener
    (on-open [_ socket]
      (wsp/on-open listener (wrap-socket socket)))
    (on-message [_ socket message]
      (wsp/on-message listener (wrap-socket socket) (<-transit message)))
    (on-pong [_ socket data]
      (wsp/on-pong listener (wrap-socket socket) data))
    (on-error [_ socket throwable]
      (wsp/on-error listener (wrap-socket socket) throwable))
    (on-close [_ socket code reason]
      (wsp/on-close listener (wrap-socket socket) code reason))))

(defn websocket-transit-response [response]
  (if (contains? response :ring.websocket/listener)
    (update response :ring.websocket/listener wrap-listener)
    response))

(defn wrap-websocket-transit [handler]
  (fn
    ([request]
     (websocket-transit-response (handler request)))
    ([request respond raise]
     (handler request (comp respond websocket-transit-response) raise))))
