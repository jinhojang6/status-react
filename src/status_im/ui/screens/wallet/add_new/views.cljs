(ns status-im.ui.screens.wallet.add-new.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.components.react :as react]
            [status-im.ui.screens.hardwallet.pin.views :as pin.views]
            [status-im.i18n :as i18n]
            [re-frame.core :as re-frame]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.list-item.views :as list-item]
            [reagent.core :as reagent]
            [cljs.spec.alpha :as spec]
            [status-im.multiaccounts.db :as multiaccounts.db]
            [status-im.ui.components.toolbar :as toolbar]
            [status-im.ui.components.styles :as components.styles]
            [status-im.ethereum.core :as ethereum]
            [status-im.ui.components.topbar :as topbar]))

(defn add-account []
  [react/view {:flex 1}
   [topbar/topbar]
   [react/scroll-view {:keyboard-should-persist-taps :handled
                       :style                        {:flex 1}}
    [react/view {:align-items :center :padding-horizontal 40 :margin-bottom 52}
     [react/text {:style {:typography :header :margin-top 16}}
      (i18n/label :t/add-an-account)]
     [react/text {:style {:color colors/gray :text-align :center :margin-top 16 :line-height 22}}
      (i18n/label :t/add-account-description)]]
    [list-item/list-item
     {:type  :section-header
      :title :t/default}]
    [list-item/list-item
     {:title       :t/generate-a-new-account
      :theme       :action
      :icon        :main-icons/add
      :accessories [:chevron]
      :on-press    #(re-frame/dispatch [:wallet.accounts/start-adding-new-account {:type :generate}])}]
    ;;TODO: implement adding account by seedphrase and private key
    #_[list-item/list-item
       {:type                 :section-header
        :container-margin-top 24
        :title                (i18n/label :t/advanced)}]
    #_[list-item/list-item
       {:title       (i18n/label :t/enter-a-seed-phrase)
        :theme       :action
        :icon        :main-icons/add
        :accessories [:chevron]
        :disabled?   true
        :on-press    #(re-frame/dispatch [:wallet.accounts/start-adding-new-account {:type :seed}])}]
    #_[list-item/list-item
       {:title       (i18n/label :t/enter-a-private-key)
        :theme       :action
        :icon        :main-icons/add
        :accessories [:chevron]
        :disabled?   true
        :on-press    #(re-frame/dispatch [:wallet.accounts/start-adding-new-account {:type :key}])}]]])

(def input-container
  {:flex-direction     :row
   :align-items        :center
   :border-radius      components.styles/border-radius
   :height             52
   :margin             16
   :padding-horizontal 16
   :background-color   colors/gray-lighter})

(defview add-watch-account []
  (letsubs [add-account-disabled? [:add-account-disabled?]]
    [react/keyboard-avoiding-view {:flex 1}
     [topbar/topbar]
     [react/view {:flex            1
                  :justify-content :space-between
                  :align-items     :center :margin-horizontal 16}
      [react/view
       [react/text {:style {:typography :header :margin-top 16}}
        (i18n/label :t/add-a-watch-account)]
       [react/text {:style {:color colors/gray :text-align :center :margin-vertical 16}}
        (i18n/label :t/enter-watch-account-address)]]
      [react/view {:align-items :center :flex 1 :flex-direction :row}
       [react/text-input {:auto-focus        true
                          :multiline         true
                          :text-align        :center
                          :placeholder       (i18n/label :t/enter-address)
                          :style             {:typography :header :flex 1}
                          :on-change-text    #(re-frame/dispatch [:set-in [:add-account :address] %])}]]]
     [toolbar/toolbar
      {:show-border? true
       :right        {:type      :next
                      :label     (i18n/label :t/next)
                      :on-press  #(re-frame/dispatch [:wallet.accounts/add-watch-account])
                      :disabled? add-account-disabled?}}]]))

(defview pin []
  (letsubs [pin [:hardwallet/pin]
            status [:hardwallet/pin-status]
            error-label [:hardwallet/pin-error-label]]
    [react/keyboard-avoiding-view {:style {:flex 1}}
     [topbar/topbar]
     [pin.views/pin-view
      {:pin               pin
       :status            status
       :title-label       :t/current-pin
       :description-label :t/current-pin-description
       :error-label       error-label
       :step              :export-key}]]))

(defview password []
  (letsubs [{:keys [error]} [:add-account]
            entered-password (reagent/atom "")]
    [react/keyboard-avoiding-view {:style {:flex 1}}
     [topbar/topbar]
     [react/view {:flex            1
                  :justify-content :space-between
                  :align-items     :center :margin-horizontal 16}
      [react/text {:style {:typography :header :margin-top 16}} (i18n/label :t/enter-your-password)]
      [react/view {:justify-content :center :flex 1}
       [react/text-input {:secure-text-entry true
                          :auto-focus        true
                          :auto-capitalize   :none
                          :text-align        :center
                          :placeholder       ""
                          :style             {:typography :header}
                          :on-change-text    #(reset! entered-password %)}]
       (when error
         [react/text {:style {:text-align :center :color colors/red :margin-top 76}} error])]
      [react/text {:style {:color colors/gray :text-align :center :margin-bottom 16}}
       (i18n/label :t/to-encrypt-enter-password)]]
     [toolbar/toolbar
      {:show-border? true
       :right        {:type      :next
                      :label     :t/generate-account
                      :on-press  #(re-frame/dispatch [:wallet.accounts/generate-new-account @entered-password])
                      :disabled? (not (spec/valid? ::multiaccounts.db/password @entered-password))}}]]))
