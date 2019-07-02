(ns juxt.crux-ui.frontend.events.facade
  (:require [re-frame.core :as rf]
            [juxt.crux-ui.frontend.io.query :as q]
            [juxt.crux-ui.frontend.logic.query-analysis :as qa]))



; ----- effects -----

(rf/reg-fx
  :fx/query-exec
  (fn [{:keys [raw-input query-analysis] :as query}]
    (q/exec query)))

(rf/reg-fx
  :fx/query-stats
  (fn [_]
    (q/fetch-stats)))



; ----- events -----

(rf/reg-event-fx
  :evt.db/init
  (fn [_ [_ db]]
    {:db             db
     :fx/query-stats nil}))

(rf/reg-event-db
  :evt.io/stats-success
  (fn [db [_ stats]]
    (assoc db :db.meta/stats stats)))

(rf/reg-event-db
  :evt.io/query-success
  (fn [db [_ res]]
    (let [q-info (:db.query/analysis-committed db)]
      (assoc db :db.query/result
                (if (:full-results? q-info)
                  (flatten res) res)))))

(rf/reg-event-db
  :evt.io/tx-success
  (fn [db [_ res]]
    (let [q-info (:db.query/analysis-committed db)]
      (assoc db :db.query/result
                (if (:full-results? q-info)
                  (flatten res) res)))))

(rf/reg-event-fx
  :evt.keyboard/ctrl-enter
  (fn []
    {:dispatch [:evt.ui/query-submit]}))

(rf/reg-event-fx
  :evt.ui/query-submit
  (fn [{:keys [db] :as ctx}]
    (let [input (:db.query/input db)
          edn (qa/try-read-string input)
          analysis (and (not (:error edn)) (qa/analyse-query edn))]
      {:db            (-> db
                          (update :db.query/key inc)
                          (assoc :db.query/input-committed input
                                 :db.query/analysis-committed analysis
                                 :db.query/edn-committed edn
                                 :db.query/result nil))
       :fx/query-exec {:raw-input      input
                       :query-analysis analysis}})))


(rf/reg-event-db
  :evt.ui.query/time-change
  (fn [db [_ time-type time]]
    (assoc-in db [:db.query/time time-type] time)))

(rf/reg-event-db
  :evt.ui.output/tab-switch
  (fn [db [_ new-tab-id]]
    (assoc db :db.ui/output-tab new-tab-id)))

(rf/reg-event-db
  :evt.ui/query-change
  (fn [db [_ query-text]]
    (assoc db :db.query/input query-text)))
