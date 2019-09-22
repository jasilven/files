(ns adminapp.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :active-page (fn [db _] (:active-page db)))
(rf/reg-sub :selected-document (fn [db _] (:selected-document db)))
(rf/reg-sub :documents (fn [db _] (:documents db)))
(rf/reg-sub :alert (fn [db _] (:alert db)))
(rf/reg-sub :identity (fn [db _] (:identity db)))
(rf/reg-sub :token (fn [db _] (:token db)))
(rf/reg-sub :refreshing? (fn [db _] (:refreshing? db)))
(rf/reg-sub :backend (fn [db _] (:backend db)))
