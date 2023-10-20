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
    (-open? [_]             (wsp/-open? socket))
    (-send [_ message]      (wsp/-send socket (->transit message)))
    (-ping [_ data]         (wsp/-ping socket data))
    (-pong [_ data]         (wsp/-pong socket data))
    (-close [_ code reason] (wsp/-close socket code reason))
    wsp/AsyncSocket
    (-send-async [_ message succeed fail]
      (wsp/-send-async socket (->transit message) succeed fail))))

(defn- wrap-listener [listener]
  (reify wsp/Listener
    (on-open [_ socket]
      (wsp/on-open listener (wrap-socket socket)))
    (on-message [_ socket message]
      (let [socket     (wrap-socket socket)
            [ok? data] (try
                         [true (<-transit message)]
                         (catch Exception ex [false ex]))]
        (if ok?
          (wsp/on-message listener socket data)
          (wsp/on-error listener socket data))))
    (on-pong [_ socket data]
      (wsp/on-pong listener (wrap-socket socket) data))
    (on-error [_ socket throwable]
      (wsp/on-error listener (wrap-socket socket) throwable))
    (on-close [_ socket code reason]
      (wsp/on-close listener (wrap-socket socket) code reason))
    wsp/PingListener
    (on-ping [_ socket data]
      (if (satisfies? wsp/PingListener listener)
        (wsp/on-ping listener (wrap-socket socket) data)
        (wsp/-ping socket data)))))

(defn websocket-transit-response
  "Wraps a Ring WebSocket response such that incoming and outgoing messages
  are formatted using Transit. See: wrap-websocket-transit."
  [response]
  (if (contains? response :ring.websocket/listener)
    (-> response
        (update :ring.websocket/listener wrap-listener)
        (update :ring.websocket/protocol #(or % "transit+json")))
    response))

(defn wrap-websocket-transit
  "Ring middleware that translates incoming and outgoing WebSocket messages
  using the Transit data format. The ring.websocket.protocols/on-message
  method will receive data decoded from Transit, and the ring.websocket/send
  function will send data encoded into Transit."
  [handler]
  (fn
    ([request]
     (websocket-transit-response (handler request)))
    ([request respond raise]
     (handler request (comp respond websocket-transit-response) raise))))
