(ns websocket-server.core
  (:require [org.httpkit.server :as http]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]))

; In case we want to start multiple servers, we will keep them as port -> server
(defonce channel-hub (atom {}))
(defonce servers (atom {}))

(defn websocket-server [port cb req]
  (let [{:keys [in-fn out-fn]} (@servers port)]
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
           (let [resp (cb (in-fn data))]
             (log/debug "RECV: " data)
             (log/debug "RESP: " resp)
             (http/send! channel (out-fn resp)))))))))

(defn send-all!
  ([data]
   ; Warning: When calling this function without port, you are sending data on every websocket server opened, and every channel
   (doseq [port (keys @channel-hub)]
     (send-all! port data)))
  ([port data]
   (doseq [channel (keys (@channel-hub port))]
     (http/send! channel ((get-in @servers [port :out-fn]) data)))))

(defn stop-ws-server [port]
  (when (@servers port)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    ((get-in @servers [port :server]) :timeout 100)
    (swap! servers dissoc port)))

(defn start-ws-server
  ([port callback]
   (start-ws-server port callback identity identity))
  ([port callback in-fn out-fn]
   (if (@servers port)
     (stop-ws-server port))
   (let [server
         (http/run-server (partial websocket-server port callback)
                          {:port port})]
     (swap! servers assoc port
       {:server server
        :in-fn in-fn
        :out-fn out-fn})
     server)))

(defn stop-all-ws-servers []
  (doseq [port (keys @servers)]
    (stop-ws-server port)))

(comment
  (def port 8899)
  (defn handle [data]
    (println "Message handled:" data)
    data)
  ; Example to work with https://github.com/ftravers/transit-websocket-client/
  (start-ws-server
    port
    (fn [[action data]]
      [action (handle data)])
    json/read-str json/write-str)
  (send-all! port ["~#'" (str [[:back-msg] "Message from backend"])])
  ; Example to work with https://github.com/ftravers/reframe-websocket/
  (start-ws-server
    port
    (fn [[store-path data]]
      [store-path (handle data)])
    (fn [s]
      (let [[_ rf-msg] (json/read-str s)]
        (read-string rf-msg)))
    (fn [msg]
      (json/write-str
        ["~#'" (str msg)])))
  (send-all! port [[:back-msg] "Message from backend"])
  (send-all! port [[:back-msg] {:map 134 :text "EDN from backend"}])
  (stop-ws-server port))