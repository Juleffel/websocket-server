# WebSocket Server

This is the server side of the websocket connection.  This is meant to
be paired with [fentontravers/websocket-client](https://github.com/ftravers/websocket-client).

# Clojars

![](https://clojars.org/fentontravers/websocket-server/latest-version.svg)
  
# Usage

```clojure
(require '[websocket-server.core :refer :all])

(defn request-handler-upcase-string
  "The function that will take incoming data off the websocket,
  process it and return a reponse.  In our case we'll simply UPPERCASE
  whatever is received."
  [data] (clojure.string/upper-case (str data)))

; Always call this module functions with a port, unless you want to apply it to every server you opened on all ports.
(def port 8899)

(defn start
  "Demonstrate how to use the websocket server library."
  []
  (start-ws-server port :on-receive request-handler-upcase-string))

(defn send-all!
  [data]
  (send-all! port data))

(defn stop
  "Stop websocket server"
  []
  (stop-ws-server port))
```
  
Here is another example that expects EDN in the form of a map that
looks like `{:count 1}`, or just a map with a key `:count` and some
integer value.  Then it increments that value by 10 and returns it
back.

```clojure
(defn request-handler-add10 
  [data]
  (->> data
       edn/read-string
       :count
       (+ 10)
       (hash-map :count)
       str))
```

# Multiple servers usage

```clojure
(start-ws-server 8000 :on-receive request-handler-1)
(start-ws-server 8001 :on-receive request-handler-2)
(start-ws-server 8002 :on-receive request-handler-3)

; Send "Hello" to all channels opened to websocket on port 8000
(send-all! 8000 "Hello")

(stop-ws-server 8002)

; Send "Hi!" to all channels opened to websocket on port 8000 or 8001
(send-all! "Hello")

(stop-all-ws-servers)
```

# Use transformer functions when receiving and sending data

```clojure
(require '[clojure.data.json :as json])

(defn request-handler-json
    [[action data]]
    [action
     (case action
        "upcase" (request-handler-upcase-string data)
        data)])

; json/read-str will be applied before sending data to request-json-handler
; json/write-str will be applied before sending data from request-json-handler
;   back on the websocket, or when using (send-all! port data)
(start-ws-server 8000
  :on-receive request-handler-json
  :in-fn json/read-str
  :out-fn json/write-str)

```