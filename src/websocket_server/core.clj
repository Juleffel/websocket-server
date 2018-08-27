(ns websocket-server.core
  (:require [org.httpkit.server :as http]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]))

; In case we want to start multiple servers, we will keep them as port -> server
(defonce channel-hub (atom {}))
(defonce servers (atom {}))

(defn websocket-server [port on-open on-receive on-close req]
  (let [{:keys [in-fn out-fn]} (@servers port)]
    (http/with-channel req channel
      (swap! channel-hub assoc-in [port channel] req)
      (if on-open (on-open channel req))
      (http/on-close
       channel
       (fn [status]
         (log/debug (str "Websocket channel closed with status: " status))
         (if on-close (on-close status))
         (swap! channel-hub update port dissoc channel)))
      (http/on-receive
       channel
       (fn [data]
         (if (and on-receive (http/websocket? channel))
           (let [resp (on-receive (in-fn data))]
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
  [port & {:keys [on-open on-receive on-close in-fn out-fn]
           :or {in-fn identity, out-fn identity}}]
  (if (@servers port)
    (stop-ws-server port))
  (let [server
        (http/run-server (partial websocket-server port on-open on-receive on-close)
                         {:port port})]
    (swap! servers assoc port
      {:server server
       :in-fn in-fn
       :out-fn out-fn})
    server))

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
    :on-receive
    (fn [[action data]]
      [action (handle data)])
    :in-fn json/read-str
    :out-fn json/write-str)
  (send-all! port ["~#'" (str [[:back-msg] "Message from backend"])])
  ; Example to work with https://github.com/ftravers/reframe-websocket/
  (start-ws-server
    port
    :on-receive
    (fn [[store-path data]]
      [store-path (handle data)])
    :in-fn
    (fn [s]
      (let [[_ rf-msg] (json/read-str s)]
        (read-string rf-msg)))
    :out-fn
    (fn [msg]
      (json/write-str
        ["~#'" (str msg)])))
  (send-all! port [[:back-msg] "Message from backend"])
  (send-all! port [[:back-msg] {:map 134 :text "EDN from backend"}])
  (stop-ws-server port))