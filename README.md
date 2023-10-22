# Ring-WebSocket-Transit [![Build Status](https://github.com/ring-clojure/ring-websocket-transit/actions/workflows/test.yml/badge.svg?branch=master)](https://github.com/ring-clojure/ring-websocket-transit/actions/workflows/test.yml)


A Clojure library for using the [Transit][] data format over [Ring's][]
WebSocket API (currently in beta testing).

[transit]: https://github.com/cognitect/transit-format
[ring's]: https://github.com/ring-clojure/ring

## Installation

Add the following dependency to your deps.edn file:

    org.ring-clojure/ring-websocket-transit {:mvn {"0.1.0-beta2"}}

Or to your Leiningen project file:

    [org.ring-clojure/ring-websocket-transit "0.1.0-beta2"]

## Usage

The `wrap-websocket-transit` middleware function converts incoming and
outgoing WebSocket text messages to and from the Transit data format.

For example:

```clojure
(require '[ring.websocket :as ws]
         '[ring.websocket.transit :as wst])

(def handler
  (wst/wrap-websocket-transit
   (fn [_request]
     {::ws/listener
      {:on-message
       (fn [socket message]
         (ws/send socket (update message :counter inc)))}})))
```

Instead of receiving a text message via the `on-message` listener event,
a data structure decoded from Transit formatting is supplied instead.
The socket argument is also wrapped, such that sending a data structure
automatically encodes it.

This also works with libraries like [Ring-WebSocket-Async][]:

[ring-websocket-async]: https://github.com/ring-clojure/ring-websocket-async

```clojure
(require '[clojure.core.async :as a :refer [<! >!]]
         '[ring.websocket.async :as wsa])

(def handler-async
  (wst/wrap-websocket-transit
    (fn [_request]
      (wsa/go-websocket [in out]
        (loop
          (when-let [message (<! in)]
            (>! out (update message :counter inc))
            (recur)))))))
```

## License

Copyright © 2023 James Reeves

Released under the MIT license.
