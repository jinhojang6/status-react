(ns status-im.ui.screens.add-new.new-chat.db
  (:require [status-im.utils.hex :as hex]
            [status-im.ethereum.ens :as ens]
            [status-im.utils.platform :as platform]
            [status-im.i18n :as i18n]
            [cljs.spec.alpha :as spec]
            [clojure.string :as string]))

(defn own-public-key?
  [{:keys [multiaccount]} public-key]
  (= (:public-key multiaccount) public-key))

(defn validate-pub-key [db public-key]
  (cond
    (or (not (spec/valid? :global/public-key public-key))
        (= public-key ens/default-key))
    (i18n/label (if platform/desktop?
                  :t/use-valid-contact-code-desktop
                  :t/use-valid-contact-code))
    (own-public-key? db public-key)
    (i18n/label :t/can-not-add-yourself)))
