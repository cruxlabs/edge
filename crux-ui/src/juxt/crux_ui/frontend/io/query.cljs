(ns juxt.crux-ui.frontend.io.query
  (:require [clojure.core.async :as async
             :refer [take! put! <! >! timeout chan alt! go go-loop]]
            [re-frame.core :as rf]
            [juxt.crux-lib.async-http-client :as crux-api]))

(defn post-opts [body]
  #js {:method "POST"
       :body body
       :headers #js {:Content-Type "application/edn"}})

(def c (crux-api/new-api-client "http://localhost:8080"))

(defn on-exec-success [resp]
  (rf/dispatch [:evt.io/query-success resp]))

(defn on-stats-success [resp]
  (rf/dispatch [:evt.io/stats-success resp]))

(defn on-tx-success [resp]
  (rf/dispatch [:evt.io/tx-success resp]))

(defn submit-tx []
  (let [tx [[:crux.tx/put :dbpedia.resource/Pablo-Picasso3 ; id for Kafka
             {:crux.db/id :dbpedia.resource/Pablo-Picasso3 ; id for Crux
              :name "Pablo"
              :last-name "Picasso3"}]]
        promise (crux-api/submitTx c tx)]
    (.then on-tx-success)))

(defn exec [query-text]
  (let [promise (crux-api/q (crux-api/db c) query-text)]
    (.then promise on-exec-success)))

(defn fetch-stats []
  (let [p (crux-api/attributeStats c)]
    (.then p on-stats-success)))

(comment
  (exec (pr-str '{:full-results? true
                  :find [e]
                  :where [[e :name "Pablo"]]})))