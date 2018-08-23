(ns websocket-server.core
  (:require [org.httpkit.server :as http]
            [taoensso.timbre :as log]))

; In case we want to start multiple servers, we will keep them as port -> server
(defonce channel-hub (atom {}))
(defonce servers (atom {}))

(defn websocket-server [port cb req]
  (http/with-channel req channel
    (swap! channel-hub assoc-in [port channel] req)
    (http/on-close
     channel
     (fn [status]
       (log/debug (str "Websocket channel closed with status: " status))
       (swap! channel-hub update port dissoc channel)))
    (http/on-receive
     channel
     (fn [data]
       (if (http/websocket? channel)
         (let [resp (cb data)]
           (log/debug "RECV: " data)
           (log/debug "RESP: " resp)
           (http/send! channel resp)))))))

(defn send-all!
  ([data]
   ; Warning: When calling this function without port, you are sending data on every websocket server opened, and every channel
   (doseq [port (keys @channel-hub)]
     (send-all! port data)))
  ([port data]
   (doseq [channel (keys (@channel-hub port))]
     (http/send! channel data))))

(defn stop-ws-server [port]
  (when-not (nil? (@servers port))
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    ((@servers port) :timeout 100)
    (swap! servers dissoc port)))

(defn start-ws-server [port callback]
  (if (@servers port)
    (stop-ws-server port))
  (let [server
        (http/run-server (partial websocket-server port callback)
                         {:port port})]
    (swap! servers assoc port server)
    server))

(defn stop-all-ws-servers []
  (doseq [port (keys @servers)]
    (stop-ws-server port)))
