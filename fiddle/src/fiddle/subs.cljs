(ns fiddle.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub :view-id (fn [db] (get db :view-id)))

(re-frame/reg-sub :icons (fn [db] (get db :icons)))