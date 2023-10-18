(ns ring.websocket.transit)

(defn wrap-websocket-transit [handler]
  (fn
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))
