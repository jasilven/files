(ns adminapp.events
  (:require [day8.re-frame.http-fx]
            [re-frame.core :as rf :refer [subscribe dispatch]]
            [ajax.core :as ajax]))

(defn auth-header [token]
  "Return bearer auth header."
  [:Authorization (str "Bearer " token)])

(rf/reg-event-db
 :initialize
 (fn [_ backend]
   {:active-page :login
    :selected-document nil
    :documents nil
    :refreshing? false
    :alert nil
    :identity nil
    :token nil
    :backend (or backend "https://localhost:8080")}))

(rf/reg-event-fx
 :login
 (fn [_ [_ token]]
   (let [backend @(rf/subscribe [:backend])]
     {:http-xhrio {:method :post :uri (str backend "/token") :params {:token token}
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:login-success token]
                   :on-failure [:login-failure]}})))

(rf/reg-event-db
 :login-success
 (fn [db [_ token response]]
   (rf/dispatch [:clear-alert])
   (rf/dispatch [:refresh-documents])
   (assoc db :token token :identity response :active-page :document-list)))

(rf/reg-event-db
 :login-failure
 (fn [db [_ response]]
   (let [msg (or (get-in response [:response :result]) "Login failure")]
     (rf/dispatch [:alert msg])
     (assoc db :active-page :login))))

(rf/reg-event-db
 :logout
 (fn [db [_ _]]
   (assoc db :identity nil :token nil :documents nil :alert nil :active-page :login)))

(rf/reg-event-fx
 :refresh-documents
 (fn [{db :db} [_ _]]
   (let [backend @(rf/subscribe [:backend])]
     {:http-xhrio {:method :get :uri (str backend "/api/files")
                   :headers (auth-header (:token db))
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:refresh-documents-success]
                   :on-failure [:refresh-documents-failure]}
      :db (assoc db :refreshing? true)})))

(rf/reg-event-db
 :refresh-documents-success
 (fn [db [_ response]]
   (assoc db :documents response :refreshing? false)))

(rf/reg-event-db
 :refresh-documents-failure
 (fn [db [_ _]]
   (rf/dispatch [:alert "Unable to get documents from server!"])
   (assoc db :refreshing? false)))

(rf/reg-event-db
 :show-details
 (fn [db [_ doc]]
   (assoc db :selected-document doc :active-page :document-details)))

(rf/reg-event-db
 :close-details
 (fn [db [_ _]]
   (assoc db :selected-document nil :active-page :document-list)))

(rf/reg-event-db
 :alert
 (fn [db [_ msg]]
   (assoc db :alert msg)))

(rf/reg-event-db
 :clear-alert
 (fn [db [_ _]]
   (assoc db :alert nil)))
