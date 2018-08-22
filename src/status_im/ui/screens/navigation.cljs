(ns status-im.ui.screens.navigation
  (:require [re-frame.core :as re-frame]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.utils.navigation :as navigation]))

;; private helper fns

(defn- push-view [db view-id]
  (-> db
      (update :navigation-stack conj view-id)
      (assoc :view-id view-id)))

(defn- replace-top-element [stack view-id]
  (let [stack' (if (> 2 (count stack))
                 (list :home)
                 (pop stack))]
    (conj stack' view-id)))

;; public fns

(defn navigate-to-clean
  ([view-id cofx] (navigate-to-clean view-id cofx nil))
  ([view-id {:keys [db]} screen-params]
   ;; TODO (jeluard) Unify all :navigate-to flavours. Maybe accept a map of parameters?

   (let [db (cond-> (assoc db :navigation-stack (list))
              (seq screen-params)
              (assoc-in [:navigation/screen-params view-id] screen-params))]
     {:db           (push-view db view-id)
      ::navigate-to view-id})))

(defn replace-view [view-id {:keys [db]}]
  {:db (-> (update db :navigation-stack replace-top-element view-id)
           (assoc :view-id view-id))})

(defn navigate-forget [view-id {:keys [db]}]
  {:db (assoc db :view-id view-id)})

(defmulti unload-data!
  (fn [db] (:view-id db)))

(defmethod unload-data! :default [db] db)

(defmulti preload-data!
  (fn [db [_ view-id]] (or view-id (:view-id db))))

(defmethod preload-data! :default [db _] db)

(defn- -preload-data! [{:keys [was-modal?] :as db} & args]
  (if was-modal?
    (dissoc db :was-modal?) ;;TODO check how it worked with this bug
    (apply preload-data! db args)))

(defn navigate-to-cofx [go-to-view-id screen-params {:keys [db]}]
  (let [view-id (:view-id db)
        db      (cond-> db
                  (seq screen-params)
                  (assoc-in [:navigation/screen-params go-to-view-id] screen-params))]
    {:db           (if (= view-id go-to-view-id)
                     db
                     (push-view db go-to-view-id))
     ::navigate-to go-to-view-id}))

(defn navigate-to
  "DEPRECATED, use navigate-to-cofx above.
  Navigates to particular view"
  ([db go-to-view-id]
   (navigate-to db go-to-view-id nil))
  ([db go-to-view-id screen-params]
   (:db (navigate-to-cofx go-to-view-id screen-params {:db db}))))

(def unload-data-interceptor
  (re-frame/->interceptor
   :id unload-data-interceptor
   :before (fn unload-data-interceptor-before
             [context]
             (let [db (re-frame/get-coeffect context :db)]
               (re-frame/assoc-coeffect context :db (unload-data! db))))))

(def navigation-interceptors
  [unload-data-interceptor (re-frame/enrich preload-data!)])

;; effects

(re-frame/reg-fx
 ::navigate-to
 (fn [view-id]
   (navigation/navigate-to (name view-id))))

(re-frame/reg-fx
 ::navigate-back
 (fn []
   (navigation/navigate-back)))

;; event handlers

(handlers/register-handler-fx
 :navigate-to
 navigation-interceptors
 (fn [cofx [_ & [go-to-view-id screen-params]]]
   (navigate-to-cofx go-to-view-id screen-params cofx)))

(handlers/register-handler-db
 :navigate-to-modal
 navigation-interceptors
 (fn [db [_ modal-view]]
   (assoc db :modal modal-view)))

(handlers/register-handler-fx
 :navigation-replace
 navigation-interceptors
 (fn [cofx [_ view-id]]
   (replace-view view-id cofx)))

(defn navigate-back [{:keys [navigation-stack view-id modal] :as db}]
  (cond
    modal (assoc db :modal nil
                 :was-modal? true)
    (>= 1 (count navigation-stack)) db

    :else
    (let [[previous-view-id :as navigation-stack'] (pop navigation-stack)
          first-in-stack (first navigation-stack)]
      (if (= view-id first-in-stack)
        (-> db
            (assoc :view-id previous-view-id)
            (assoc :navigation-stack navigation-stack'))
        (assoc db :view-id first-in-stack)))))

(handlers/register-handler-fx
 :navigate-back
 (re-frame/enrich -preload-data!)
 (fn [{:keys [db]} _]
   (let [{:keys [view-id] :as new-id} (navigate-back db)]
     {:db           (navigate-back new-id)
      ::navigate-to view-id})))

(handlers/register-handler-fx
 :navigate-to-clean
 (fn [cofx [_ view-id params]]
   (navigate-to-clean view-id cofx params)))

(handlers/register-handler-fx
 :navigate-to-tab
 navigation-interceptors
 (fn [{:keys [db] :as cofx} [_ view-id]]
   (handlers-macro/merge-fx cofx
                            {:db (-> db
                                     (assoc :prev-tab-view-id (:view-id db))
                                     (assoc :prev-view-id (:view-id db)))}
                            (navigate-to-clean view-id))))
