(ns adminapp.core
  (:require [reagent.core :as reagent]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [re-frame.core :as rf]
            [adminapp.views :as v]))

(def default-db {:active-page :login
                 :selected-document nil
                 :selected-document-logs nil
                 :documents nil
                 :refreshing? false
                 :alert nil
                 :identity nil
                 :token nil
                 :backend "https://localhost:8080"})
;; EVENTS
(defn auth-header [token]
  "Return bearer auth header."
  [:Authorization (str "Bearer " token)])

(rf/reg-event-fx
 :initialize
 (fn [world [_ backend]]
   (assoc world :db (assoc default-db :backend backend))))

(rf/reg-event-db
 :set-backend
 (fn [db [_ backend]]
   (assoc db :backend backend)))

(rf/reg-event-db
 :set-active-page
 (fn [db [_ page]]
   (assoc db :active-page page)))

(rf/reg-event-fx
 :login
 (fn [world [_ token]]
   (let [backend @(rf/subscribe [:backend])]
     {:http-xhrio {:method :post :uri (str backend "/login") :params {:token token}
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:login-success token]
                   :on-failure [:login-failure]}
      :db (:db world)})))

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

(rf/reg-event-fx
 :logout
 (fn [world [_ _]]
   (assoc world :db (assoc default-db :backend (get-in world [:db :backend])))))

(rf/reg-event-fx
 :fetch-document
 (fn [{db :db} [_ id]]
   (let [backend @(rf/subscribe [:backend])]
     {:http-xhrio {:method :get :uri (str backend "/api/files/" id "?binary=no")
                   :headers (auth-header (:token db))
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:fetch-document-success]
                   :on-failure [:fetch-document-failure]}
      :db db})))

(rf/reg-event-db
 :fetch-document-success
 (fn [db [_ response]]
   (let [doc (v/json->clj response)]
     (assoc db :active-page :document-details :selected-document doc))))

(rf/reg-event-db
 :fetch-document-failure
 (fn [db [_ response]]
   (assoc db :alert (or (get-in response [:response :result]) "Document not found!"))))

(rf/reg-event-fx
 :fetch-logs
 (fn [{db :db} [_ id]]
   (let [backend @(rf/subscribe [:backend])]
     {:http-xhrio {:method :get :uri (str backend "/admin/auditlogs/" id)
                   :headers (auth-header (:token db))
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:fetch-logs-success]
                   :on-failure [:fetch-logs-failure]}
      :db db})))

(rf/reg-event-db
 :clear-logs
 (fn [db [_ _]]
   (assoc db :selected-document-logs nil)))

(rf/reg-event-db
 :fetch-logs-success
 (fn [db [_ response]]
   (let [logs (v/json->clj response)]
     (assoc db :selected-document-logs logs))))

(rf/reg-event-db
 :fetch-logs-failure
 (fn [db [_ response]]
   (assoc db :alert (or (get-in response [:response :result]) "Logs not found!"))))

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
   (assoc db :documents response :refreshing? false :active-page :document-list)))

(rf/reg-event-db
 :refresh-documents-failure
 (fn [db [_ _]]
   (assoc db :refreshing? false :alert "Unable to get documents from server!")))

(rf/reg-event-db
 :show-details
 (fn [db [_ doc]]
   (assoc db :selected-document doc :active-page :document-details)))

(rf/reg-event-db
 :close-details
 (fn [db [_ _]]
   (assoc db
          :selected-document nil
          :selected-document-logs nil
          :active-page :document-list)))

(rf/reg-event-db
 :alert
 (fn [db [_ msg]]
   (assoc db :alert msg)))

(rf/reg-event-db
 :clear-alert
 (fn [db [_ _]]
   (assoc db :alert nil)))

;; SUBS
(rf/reg-sub :active-page (fn [db _] (:active-page db)))
(rf/reg-sub :selected-document (fn [db _] (:selected-document db)))
(rf/reg-sub :selected-document-logs (fn [db _] (:selected-document-logs db)))
(rf/reg-sub :documents (fn [db _] (:documents db)))
(rf/reg-sub :alert (fn [db _] (:alert db)))
(rf/reg-sub :identity (fn [db _] (:identity db)))
(rf/reg-sub :token (fn [db _] (:token db)))
(rf/reg-sub :refreshing? (fn [db _] (:refreshing? db)))
(rf/reg-sub :backend (fn [db _] (:backend db)))

(defn page [p]
  (case p
    :login [v/login-page]
    :document-list [v/document-list-page]
    :document-details [v/document-details-page]
    (rf/dispatch [:alert "Page not found! This is bad."])))

(defn app []
  (let [active-page @(rf/subscribe [:active-page])
        alert @(rf/subscribe [:alert])]
    [:div
     [v/top-navi]
     (when alert [v/alert-panel alert])
     [page active-page]]))

(defn main []
  (let [url (.-location js/document)
        backend (str (.-protocol url) "//" (.-hostname url) ":" (.-port url))]
    (enable-console-print!)
    (rf/dispatch-sync [:initialize backend])
    (reagent/render [app] (js/document.getElementById "app"))))

(defn reload []
  (rf/dispatch-sync [:set-backend "https://localhost:8080"])
  (reagent/render [app] (js/document.getElementById "app")))

;; (require '[re-frame.core :as rf])
;; (rf/dispatch [:set-backend "https://localhost:8080"])
;; (println @(rf/subscribe [:backend]))
