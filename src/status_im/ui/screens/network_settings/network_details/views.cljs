(ns status-im.ui.screens.network-settings.network-details.views
  (:require-macros [status-im.utils.views :as views])
  (:require
   [re-frame.core :as rf]
   [status-im.ui.components.status-bar.view :as status-bar]
   [status-im.ui.components.toolbar.view :as toolbar]
   [status-im.ui.components.react :as react]
   [status-im.i18n :as i18n]
   [status-im.ui.components.styles :as components.styles]
   [status-im.ui.components.common.common :as components.common]
   [status-im.ui.screens.network-settings.styles :as st]
   [status-im.ui.screens.network-settings.views :as network-settings]))

(views/defview network-details []
  (views/letsubs [{:keys [networks/selected-network]} [:get-screen-params]
                  {:keys [network]} [:account/account]
                  networks          [:get-networks]]
    (let [{:keys [id name config]} selected-network
          connected?               (= id network)
          custom?                  (seq (filter #(= (:id %) id) (:custom networks)))]
      [react/view st/container
       [status-bar/status-bar]
       [react/view components.styles/flex
        [toolbar/simple-toolbar (i18n/label :t/network-details)]
        [react/view components.styles/flex
         [network-settings/network-badge
          {:name       name
           :connected? connected?}]
         (when-not connected?
           [react/touchable-highlight {:on-press #(rf/dispatch [:network.ui/connect-network-pressed id])}
            [react/view st/connect-button-container
             [react/view {:style               st/connect-button
                          :accessibility-label :network-connect-button}
              [react/text {:style st/connect-button-label}
               (i18n/label :t/connect)]]
             [react/i18n-text {:style st/connect-button-description
                               :key   :connecting-requires-login}]]])
         [react/view st/network-config-container
          [react/text {:style               st/network-config-text
                       :accessibility-label :network-details-text}
           config]]]
        (when custom?
          [react/view st/bottom-container
           [react/view components.styles/flex
            [components.common/button {:label        (i18n/label :t/delete)
                                       :button-style st/delete-button
                                       :label-style  st/delete-button-text
                                       :on-press     #(rf/dispatch [:network.ui/delete-network-pressed id])}]]])]])))
