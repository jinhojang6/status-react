(ns status-im.data-store.realm.schemas.base.v9.core
  (:require [taoensso.timbre :as log]))


(defn migration [old-realm new-realm]
  (log/debug "migrating base database v8: " old-realm new-realm))
