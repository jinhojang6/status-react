(ns status-im.wallet.accounts.core
  (:require [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.eip55 :as eip55]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.i18n :as i18n]
            [status-im.multiaccounts.update.core :as multiaccounts.update]
            [status-im.native-module.core :as status]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.list-selection :as list-selection]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.fx :as fx]
            [status-im.utils.types :as types]
            [status-im.wallet.core :as wallet]))

(re-frame/reg-fx
 :list.selection/open-share
 (fn [obj]
   (list-selection/open-share obj)))

(re-frame/reg-fx
 ::generate-account
 (fn [{:keys [derivation-info hashed-password path-num]}]
   (let [{:keys [address path]} derivation-info]
     (status/multiaccount-load-account
      address
      hashed-password
      (fn [value]
        (let [{:keys [id error]} (types/json->clj value)]
          (if error
            (re-frame/dispatch [::generate-new-account-error])
            (status/multiaccount-derive-addresses
             id
             [path]
             (fn [result]
               (status/multiaccount-store-derived
                id
                [path]
                hashed-password
                (fn [result]
                  (let [{:keys [publicKey address]}
                        (get (types/json->clj result) (keyword path))]
                    (re-frame/dispatch [:wallet.accounts/account-generated
                                        {:name (str "Account " path-num)
                                         :address address
                                         :public-key publicKey
                                         :path       (str constants/path-wallet-root "/" path-num)
                                         :color      (rand-nth colors/account-colors)}])))))))))))))

(fx/defn set-symbol-request
  {:events [:wallet.accounts/share]}
  [_ address]
  {:list.selection/open-share {:message (eip55/address->checksum address)}})

(fx/defn generate-new-account
  {:events [:wallet.accounts/generate-new-account]}
  [{:keys [db]} password]
  (let [wallet-root-address (get-in db [:multiaccount :wallet-root-address])
        path-num            (inc (get-in db [:multiaccount :latest-derived-path]))]
    (when-not (get-in db [:add-account :step])
      {:db                (assoc-in db [:add-account :step] :generating)
       ::generate-account {:derivation-info (if wallet-root-address
                                              ;; Use the walllet-root-address for stored on disk keys
                                              ;; This needs to be the RELATIVE path to the key used to derive
                                              {:path    (str "m/" path-num)
                                               :address wallet-root-address}
                                              ;; Fallback on the master account for keycards, use the absolute path
                                              {:path    (str constants/path-wallet-root "/" path-num)
                                               :address (get-in db [:multiaccount :address])})
                           :path-num        path-num
                           :hashed-password (ethereum/sha3 password)}})))

(fx/defn generate-new-account-error
  {:events [::generate-new-account-error]}
  [{:keys [db]} password]
  {:db (assoc db
              :add-account
              {:error (i18n/label :t/add-account-incorrect-password)})})

(fx/defn account-generated
  {:events [:wallet.accounts/account-generated]}
  [{:keys [db] :as cofx} account]
  (fx/merge cofx
            {:db (update db :add-account assoc :account account :step :generated)}
            (navigation/navigate-to-cofx :account-added nil)))

(fx/defn save-account
  {:events [:wallet.accounts/save-account]}
  [{:keys [db] :as cofx} account {:keys [name color]}]
  (let [accounts (:multiaccount/accounts db)
        new-account  (cond-> account
                       name (assoc :name name)
                       color (assoc :color color))
        new-accounts (replace {account new-account} accounts)]
    {::json-rpc/call [{:method     "accounts_saveAccounts"
                       :params     [[new-account]]
                       :on-success #()}]
     :db (assoc db :multiaccount/accounts new-accounts)}))

(fx/defn delete-account
  {:events [:wallet.accounts/delete-account]}
  [{:keys [db] :as cofx} account]
  (let [accounts (:multiaccount/accounts db)
        new-accounts (vec (remove #(= account %) accounts))
        deleted-address (get-in account [:address])]
    (fx/merge cofx
              {::json-rpc/call [{:method     "accounts_deleteAccount"
                                 :params     [(:address account)]
                                 :on-success #()}]
               :db (-> db
                       (assoc :multiaccount/accounts new-accounts)
                       (assoc-in [:wallet :accounts deleted-address] nil))}
              (navigation/navigate-to-cofx :wallet nil))))

(fx/defn save-generated-account
  {:events [:wallet.accounts/save-generated-account]}
  [{:keys [db] :as cofx}]
  (let [{:keys [latest-derived-path]} (:multiaccount db)
        {:keys [account path type]} (:add-account db)
        accounts (:multiaccount/accounts db)
        new-accounts (conj accounts account)]
    (when account
      (fx/merge cofx
                {::json-rpc/call [{:method     "accounts_saveAccounts"
                                   :params     [[account]]
                                   :on-success #()}]
                 :db (-> db
                         (assoc :multiaccount/accounts new-accounts)
                         (dissoc :add-account))}
                (when (= type :generate)
                  (multiaccounts.update/multiaccount-update
                   :latest-derived-path (inc latest-derived-path)
                   {}))
                (wallet/update-balances nil)
                (navigation/navigate-to-cofx :wallet nil)))))

(fx/defn start-adding-new-account
  {:events [:wallet.accounts/start-adding-new-account]}
  [{:keys [db] :as cofx} {:keys [type] :as add-account}]
  (let [{:keys [keycard-pairing]} (:multiaccount db)
        screen (case type
                 :generate (if keycard-pairing :add-new-account-pin
                               :add-new-account-password)
                 :watch :add-watch-account)]
    (fx/merge cofx
              {:db (cond-> (assoc db :add-account add-account)
                     keycard-pairing
                     (assoc-in [:hardwallet :pin :enter-step] :export-key))}
              (navigation/navigate-to-cofx screen nil))))

(fx/defn enter-phrase-next-pressed
  {:events [:wallet.accounts/enter-phrase-next-pressed]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (-> db
                     (dissoc :intro-wizard)
                     (assoc-in [:add-account :seed] (get-in db [:intro-wizard :passphrase])))}
            (navigation/navigate-to-cofx :add-new-account-password nil)))

(fx/defn add-watch-account
  {:events [:wallet.accounts/add-watch-account]}
  [{:keys [db] :as cofx}]
  (let [address (get-in db [:add-account :address])]
    (fx/merge cofx
              {:db (assoc-in db [:add-account :account]
                             {:name       ""
                              :address    (eip55/address->checksum (ethereum/normalized-hex address))
                              :type       :watch
                              :color      (rand-nth colors/account-colors)})}
              (navigation/navigate-to-cofx :account-added nil))))
