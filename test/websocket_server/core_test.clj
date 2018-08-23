(ns websocket-server.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :refer [chan <!! >!!]]
    [websocket-server.core :refer :all]
    [clojure.edn :as edn]
    [gniazdo.core :as ws]))

(defn request-handler-upcase [data]
  (clojure.string/upper-case (str data)))

(def port 7890)

(defn start
  "Demonstrate how to use the websocket server library."
  []
  (start-ws-server port request-handler-upcase))

(defn stop
  "Stop websocket server"
  []
  (stop-ws-server port))

(defn restart [] (stop) (start))

(deftest send-msg-and-check-resp
  (start-ws-server port request-handler-upcase)
  (let [ch (chan)
        client-ws
        (ws/connect
           (str "ws://localhost:" port)
           :on-receive #(>!! ch %))]
    (is (some? client-ws))
    (ws/send-msg client-ws "Hello")
    (is (= "HELLO" (<!! ch)))
    (ws/close client-ws))
  (stop-ws-server port))

(deftest send-all-test
  (start-ws-server port #(throw (Exception. "Shouldn't be called as we are initiating messages on the server side")))
  (let [ch (chan)
        n 3
        clients-ws
        (doall
          (for [i (range n)]
            (ws/connect
              (str "ws://localhost:" port)
              :on-receive #(>!! ch [i %]))))]
    (send-all! port "Test")
    (let [resps (set (repeatedly n #(<!! ch)))]
      (is (= resps
             (set (for [i (range n)] [i "Test"]))))
      (doseq [client-ws clients-ws]
        (ws/close client-ws))))
  (stop-ws-server port))


(deftest multiple-servers-test
  (let [n 3]
    (doseq [i (range 3)]
      (start-ws-server (+ 8000 i) #(throw (Exception. "Shouldn't be called as we are initiating messages on the server side"))))
    (let [ch (chan)
          clients-ws
          (doall
            (for [i (range n)]
              (ws/connect
                (str "ws://localhost:" (+ 8000 i))
                :on-receive #(>!! ch [i %]))))]
      (ws/close (nth clients-ws 2))
      (stop-ws-server 8002)
      (send-all! "Test1")
      (let [resps (set (repeatedly (dec n) #(<!! ch)))]
        (is (= resps
               (set (for [i (range (dec n))] [i "Test1"])))))
      (send-all! 8000 "Test2")
      (let [resp (<!! ch)]
        (is (= resp [0 "Test2"])))
      (ws/close (nth clients-ws 0))
      (ws/close (nth clients-ws 1))
      (stop-all-ws-servers))))