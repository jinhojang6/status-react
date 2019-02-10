(ns status-im.ui.screens.wallet.send.db
  (:require [cljs.spec.alpha :as spec]
            [status-im.utils.money :as money]
            [status-im.utils.security :as security]))

; transaction
(spec/def ::from (spec/nilable string?))
(spec/def ::to (spec/nilable string?))
(spec/def ::amount (spec/nilable money/valid?))
(spec/def ::gas (spec/nilable money/valid?))
(spec/def ::original-gas (spec/nilable money/valid?))
(spec/def ::gas-price (spec/nilable money/valid?))
; dapp transaction
(spec/def ::data (spec/nilable string?))
(spec/def ::nonce (spec/nilable string?))

(spec/def ::to-name (spec/nilable string?))
(spec/def ::amount-error (spec/nilable string?))
(spec/def ::asset-error (spec/nilable string?))
(spec/def ::amount-text (spec/nilable string?))
(spec/def ::password (spec/nilable #(instance? security/MaskedData %)))
(spec/def ::wrong-password? (spec/nilable boolean?))
(spec/def ::id (spec/nilable string?))
(spec/def ::show-password-input? (spec/nilable boolean?))
(spec/def ::height double?)
(spec/def ::width double?)
(spec/def ::camera-flashlight #{:on :off})
(spec/def ::in-progress? boolean?)
(spec/def ::from-chat? (spec/nilable boolean?))
(spec/def ::symbol (spec/nilable keyword?))
(spec/def ::advanced? boolean?)
(spec/def ::public-key (spec/nilable string?))
(spec/def ::method (spec/nilable string?))
(spec/def ::tx-hash (spec/nilable string?))
(spec/def ::on-result (spec/nilable any?))
(spec/def ::on-error (spec/nilable any?))

(spec/def :wallet/send-transaction (spec/keys :opt-un [::amount ::to ::to-name ::amount-error ::asset-error ::amount-text
                                                       ::password ::show-password-input? ::id ::from ::data ::nonce
                                                       ::camera-flashlight ::in-progress? ::on-result ::on-error
                                                       ::wrong-password? ::from-chat? ::symbol ::advanced?
                                                       ::gas ::gas-price ::original-gas ::public-key ::method ::tx-hash]))
