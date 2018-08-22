(ns status-im.protocol.models
  (:require [status-im.constants :as constants]
            [status-im.utils.semaphores :as semaphores]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.handlers-macro :as handlers-macro]))

(defn update-sync-state
  [{:keys [sync-state sync-data] :as db} error sync]
  (let [{:keys [highestBlock currentBlock] :as state}
        (js->clj sync :keywordize-keys true)
        syncing?  (> (- highestBlock currentBlock) constants/blocks-per-hour)
        new-state (cond
                    error :offline
                    syncing? (if (= sync-state :done)
                               :pending
                               :in-progress)
                    :else (if (or (= sync-state :done)
                                  (= sync-state :pending))
                            :done
                            :synced))]
    (cond-> db
      (when (and (not= sync-data state) (= :in-progress new-state)))
      (assoc :sync-data state)
      (when (not= sync-state new-state))
      (assoc :sync-state new-state))))

(defn check-sync-state
  [{{:keys [web3] :as db} :db :as cofx}]
  (if (:account/account db)
    {::web3-get-syncing web3
     :dispatch-later    [{:ms 10000 :dispatch [:check-sync-state]}]}
    (semaphores/free :check-sync-state? cofx)))

(defn start-check-sync-state
  [{{:keys [network account/account] :as db} :db :as cofx}]
  (when (and (not (semaphores/locked? :check-sync-state? cofx))
             (not (ethereum/network-with-upstream-rpc? (get-in account [:networks network]))))
    (handlers-macro/merge-fx cofx
                             {:dispatch [:check-sync-state]}
                             (semaphores/lock :check-sync-state?))))
