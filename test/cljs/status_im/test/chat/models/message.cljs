(ns status-im.test.chat.models.message
  (:require [cljs.test :refer-macros [deftest is testing]]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.utils.identicon :as identicon]
            [status-im.constants :as constants]
            [status-im.utils.datetime :as time]
            [status-im.transport.message.protocol :as protocol]
            [status-im.chat.models.message-list :as models.message-list]

            [status-im.chat.models.message :as message]
            [status-im.utils.datetime :as time]))

(deftest add-to-chat?
  (testing "it returns true when it's not in loaded message"
    (is (message/add-to-chat? {:db {:chats {"a" {}}}}
                              {:message-id "message-id"
                               :from "a"
                               :clock-value 1
                               :chat-id "a"})))
  (testing "it returns false when it's already in the loaded message"
    (is (not (message/add-to-chat? {:db {:chats {"a" {:messages {"message-id" {}}}}}}
                                   {:message-id "message-id"
                                    :from "a"
                                    :clock-value 1
                                    :chat-id "a"}))))
  (testing "it returns false when the clock-value is the same as the deleted-clock-value in chat"
    (is (not (message/add-to-chat? {:db {:chats {"a" {:deleted-at-clock-value 1}}}}
                                   {:message-id "message-id"
                                    :from "a"
                                    :clock-value 1
                                    :chat-id "a"}))))
  (testing "it returns true when the clock-value is greater than the deleted-clock-value in chat"
    (is (message/add-to-chat? {:db {:chats {"a" {:deleted-at-clock-value 1}}}}
                              {:message-id "message-id"
                               :from "a"
                               :clock-value 2
                               :chat-id "a"})))
  (testing "it returns false when the clock-value is less than the deleted-clock-value in chat"
    (is (not (message/add-to-chat? {:db {:chats {"a" {:deleted-at-clock-value 1}}}}
                                   {:message-id "message-id"
                                    :from "a"
                                    :clock-value 0
                                    :chat-id "a"})))))

(deftest add-own-received-message
  (let [db {:multiaccount {:public-key "me"}
            :view-id :chat
            :current-chat-id "chat-id"
            :chats {"chat-id" {:messages {}}}}]
    (testing "a message coming from you!"
      (let [actual (message/receive-one {:db db}
                                        {:from "me"
                                         :message-type constants/message-type-one-to-one
                                         :timestamp 0
                                         :whisper-timestamp 0
                                         :message-id "id"
                                         :chat-id "chat-id"
                                         :outgoing true
                                         :content "b"
                                         :clock-value 1})
            message (get-in actual [:db :chats "chat-id" :messages "id"])]
        (testing "it adds the message"
          (is message))))))

(deftest receive-group-chats
  (let [cofx                 {:db {:chats {"chat-id" {:contacts #{"present"}
                                                      :members-joined #{"a"}}}
                                   :multiaccount {:public-key "a"}
                                   :current-chat-id "chat-id"
                                   :view-id :chat}}
        cofx-without-member  (update-in cofx [:db :chats "chat-id" :members-joined] disj "a")
        valid-message        {:chat-id     "chat-id"
                              :from        "present"
                              :message-type constants/message-type-private-group
                              :message-id  "1"
                              :clock-value 1
                              :whisper-timestamp 0
                              :timestamp   0}
        bad-chat-id-message  {:chat-id     "bad-chat-id"
                              :from        "present"
                              :message-type constants/message-type-private-group
                              :message-id  "1"
                              :clock-value 1
                              :whisper-timestamp 0
                              :timestamp   0}
        bad-from-message     {:chat-id     "chat-id"
                              :from        "not-present"
                              :message-type constants/message-type-private-group
                              :message-id  "1"
                              :clock-value 1
                              :whisper-timestamp 0
                              :timestamp   0}]
    (testing "a valid message"
      (is (get-in (message/receive-one cofx valid-message) [:db :chats "chat-id" :messages "1"])))
    (testing "a message from someone not in the list of participants"
      (is (not (message/receive-one cofx bad-from-message))))
    (testing "a message with non existing chat-id"
      (is (not (message/receive-one cofx bad-chat-id-message))))
    (testing "a message from a delete chat"
      (is (not (message/receive-one cofx-without-member valid-message))))))

(deftest receive-public-chats
  (let [cofx                 {:db {:chats {"chat-id" {:public? true}}
                                   :multiaccount {:public-key "a"}
                                   :current-chat-id "chat-id"
                                   :view-id :chat}}
        valid-message        {:chat-id     "chat-id"
                              :from        "anyone"
                              :message-type constants/message-type-public-group
                              :message-id  "1"
                              :clock-value 1
                              :whisper-timestamp 0
                              :timestamp   0}
        bad-chat-id-message  {:chat-id     "bad-chat-id"
                              :from        "present"
                              :message-type constants/message-type-public-group
                              :message-id  "1"
                              :clock-value 1
                              :whisper-timestamp 0
                              :timestamp   0}]
    (testing "a valid message"
      (is (get-in (message/receive-one cofx valid-message) [:db :chats "chat-id" :messages "1"])))
    (testing "a message with non existing chat-id"
      (is (not (message/receive-one cofx bad-chat-id-message))))))

(deftest receive-one-to-one
  (with-redefs [gfycat/generate-gfy (constantly "generated")
                identicon/identicon (constantly "generated")]

    (let [cofx                 {:db {:chats {"matching" {}}
                                     :multiaccount {:public-key "me"}
                                     :current-chat-id "matching"
                                     :view-id :chat}}
          valid-message        {:chat-id     "matching"
                                :from        "matching"
                                :message-type constants/message-type-one-to-one
                                :message-id  "1"
                                :clock-value 1
                                :whisper-timestamp 0
                                :timestamp   0}
          own-message          {:chat-id     "matching"
                                :from        "me"
                                :message-type constants/message-type-one-to-one
                                :message-id  "1"
                                :clock-value 1
                                :whisper-timestamp 0
                                :timestamp   0}

          bad-chat-id-message  {:chat-id     "bad-chat-id"
                                :from        "not-matching"
                                :message-type constants/message-type-one-to-one
                                :message-id  "1"
                                :clock-value 1
                                :whisper-timestamp 0
                                :timestamp   0}]
      (testing "a valid message"
        (is (get-in (message/receive-one cofx valid-message) [:db :chats "matching" :messages "1"])))
      (testing "our own message"
        (is (get-in (message/receive-one cofx own-message) [:db :chats "matching" :messages "1"])))
      (testing "a message with non matching chat-id"
        (is (get-in (message/receive-one cofx bad-chat-id-message) [:db :chats "not-matching" :messages "1"]))))))

(deftest delete-message
  (with-redefs [time/day-relative (constantly "day-relative")
                time/timestamp->time (constantly "timestamp")]
    (let [cofx1     {:db {:chats {"chat-id" {:messages      {0 {:message-id  0
                                                                :content     "a"
                                                                :clock-value 0
                                                                :whisper-timestamp 0
                                                                :timestamp   0}
                                                             1 {:message-id  1
                                                                :content     "b"
                                                                :clock-value 1
                                                                :whisper-timestamp 1
                                                                :timestamp   1}}
                                             :message-list [{:something :something}]}}}}
          cofx2     {:db {:chats {"chat-id" {:messages      {0 {:message-id  0
                                                                :content     "a"
                                                                :clock-value 0
                                                                :whisper-timestamp 1
                                                                :timestamp   1}}
                                             :message-list [{:something :something}]}}}}
          fx1       (message/delete-message cofx1 "chat-id" 1)
          fx2       (message/delete-message cofx2 "chat-id" 0)]
      (testing "Deleting message deletes it along with all references"
        (is (= '(0)
               (keys (get-in fx1 [:db :chats "chat-id" :messages]))))
        (is (= [{:one-to-one? false
                 :message-id 0
                 :whisper-timestamp 0
                 :type :message
                 :display-photo? true
                 :system-message? false
                 :last-in-group? true
                 :datemark "day-relative"
                 :clock-value 0
                 :first-in-group? true
                 :from nil
                 :first-outgoing? false
                 :outgoing-seen? false
                 :timestamp-str "timestamp"
                 :first? true
                 :display-username? true
                 :outgoing false}]
               (models.message-list/->seq
                (get-in fx1 [:db :chats "chat-id" :message-list]))))
        (is (= {}
               (get-in fx2 [:db :chats "chat-id" :messages])))
        (is (= nil
               (get-in fx2 [:db :chats "chat-id" :message-list])))))))
