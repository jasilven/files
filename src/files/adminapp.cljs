(ns files.adminapp
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [clojure.string :as s]
            [goog.dom :as dom]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce documents (r/atom []))
(defonce ident (r/atom nil))
(defonce updater (r/atom nil))
(defonce backend (r/atom nil))
(def alert (r/atom nil))

(defn clj->json [ds pretty]
  (if pretty
    (.stringify js/JSON (clj->js ds) nil 2)
    (.stringify js/JSON (clj->js ds))))

(defn json->clj [json]
  (js->clj json :keywordize-keys true))

(defn show-alert []
  [:div.alert.alert-info.w-50.mx-auto
   {:role "alert" :on-click #(reset! alert nil)} (:msg @alert)])

(defn update-documents [limit]
  (go (let [response (<! (http/get (str @backend "/api/files") {:oauth-token (:token @ident)}))]
        (if (= 200 (:status response))
          (reset! documents (json->clj (:body response)))
          (reset! alert {:type "alert-danger" :msg "Server not responding!"})))))

(defn toggle-updater []
  (if @updater
    (do
      (js/clearInterval @updater)
      (reset! updater nil))
    (reset! updater (js/setInterval #(when (some? @ident)
                                       (update-documents 50)) 60000))))

(defn send-to-browser [binary filename mimetype]
  (let [blob (js/Blob. #js [binary] #js {:type mimetype})
        url (.createObjectURL (.-URL js/window) blob)
        link (dom/createDom "a" #js {"href" url})]
    (dom/appendChild (.-body js/document) link)
    (set! (.-download link) filename)
    (.click link)
    (dom/removeChildren link)))

(defn download [doc]
  (go (let [response (<! (http/get (str @backend "/api/files/" (:id doc) "/download")
                                   {:oauth-token (:token @ident)
                                    :response-type :array-buffer}))]
        (if (= 200 (:status response))
          (send-to-browser (:body response) (:filename doc) (:mimetype doc))
          (reset! alert {:type "alert-danger"
                         :msg (str "Unable to get document " (:filename doc))})))))

(defn top-navi []
  [:nav.navbar {:style {:background-color "#990AE3"}}
   [:ul.nav.mr-auto
    [:li.nav-item>a.nav-link.font-weight-bold {:style {:color "#ffffff"}} "Files"]
    [:li.nav-item>a.nav-link.text-bolder {:style {:color "#eeeeee"} :href "/swagger" :target "_blank"} "Docs"]
    (when (some? @ident)
      [:li.nav-item>a.nav-link.text-white-50.font-italic [:span @backend]])]
   (when (some? @ident)
     [:ul.nav.justify-content-end
      [:li.nav-item.nav-link {:style {:color "#eeeeee"}}
       [:div.form-check
        [:input.form-check-input {:id "updater":type "checkbox" :value ""
                                  :on-click #(toggle-updater)}]
        [:label.form-check-label {:for "updater"} "autorefresh"]]]
      [:li.nav-item>a.nav-link.font-weight-bolder
       {:style {:color "#eeeeee"}} (str "🎗") [:span.font-italic.ml-1 (:user @ident)]]
      [:li.nav-item>a.nav-link.font-weight-bolder
       {:style {:color "#4A0C6A"} :on-click #(reset! ident nil)} "Logout"]])])

(defn validate-token [t]
  (go (let [response (<! (http/post (str @backend "/token") {:json-params {:token @t}}))]
        (if (= 200 (:status response))
          (do
            (reset! ident (assoc (json->clj (:body response)) :token @t))
            (update-documents 10)
            (reset! t ""))
          (reset! alert {:type "auth" :msg "Invalid token!"})))))

(defn login []
  (let [value (r/atom "")]
    (fn []
      [:div
       [top-navi]
       (when @alert (show-alert))
       [:div.card.mx-auto.mt-5 {:style {:width "800px"}}
        [:div.card-header "Sign in with token"]
        [:div.card-body
         [:input.mb-2.form-control
          {:type "text" :placeholder "token here" :value @value
           :on-change #(reset! value (-> % .-target .-value))
           :on-key-press #(when (= (.-key %) "Enter") (validate-token value))}]
         [:input {:type "button" :value "Login" :on-click #(validate-token value)}]]]])))

(defn document-metadata [metadata]
  [:div.card-body
   [:h4 "Metadata"]
   [:table.table.table-sm.table-striped
    [:tbody
     (doall
      (for [key (sort (keys metadata))]
        ^{:key key}
        [:tr [:td.font-weight-bolder key] [:td (get metadata key)]]))]]])

(defn toggle-json [json doc]
  (if @json
    (reset! json nil)
    (reset! json (clj->json doc true))))

(defn show-json [json]
  [:div.card-body
   [:pre.text-monospace @json]
   [:button.btn.btn-sm.btn-primary.ml-0
    {:type "button"
     :on-click #(send-to-browser @json "data.json"
                                 "application/json")} "download"]
   [:button.btn.btn-sm.btn-outline-secondary.ml-2
    {:type "button" :on-click #(reset! json nil)} "close"]])

(defn document-details [doc json]
  [:div.card.mx-auto.my-3 {:style {:width "47rem"}}
   [:div.card-header
    [:button.btn.btn-link {:on-click #(reset! doc nil)} "Back"]
    [:h3.text-center.font-weight-bolder (:filename @doc)]
    [:table.table-sm.table-borderless>tbody
     (doall
      (for [[key val] (sort-by first (seq @doc))
            :when (and (some? val) (not= key :metadata))
            :let [key (str (s/capitalize (name key)) ":")]]
        ^{:key key}
        [:tr
         [:td.font-weight-bolder {:style {:text-align "right"}} key]
         (if (= key "Filename:")
           [:td.btn-link.button {:type "button" :on-click #(download @doc)} val]
           [:td val])]))]
    [:div.mt-3
     [:button.btn.btn-sm.btn-outline-primary.ml-3
      {:type "button"
       :on-click #(toggle-json json @doc)} "json"]]]
   (if @json
     (show-json json)
     [document-metadata (:metadata @doc)])])

(defn document-list [selected-doc]
  (when-not (empty? @documents)
    (let [keys [:filename :mimetype :created :updated :closed]]
      [:div
       [:div.row.text-center>div.col.pt-2
        [:h5 "Latest " (count @documents) " documents"]]
       [:table.table.table-hover
        [:thead.thead-light>tr
         (doall
          (for [key keys]
            ^{:key key} [:th {:scope "col"} (s/capitalize (name key))]))]
        [:tbody
         (doall
          (for [doc @documents]
            ^{:key (:id doc)}
            [:tr {:on-click #(reset! selected-doc doc)}
             [:th {:scope "row"} ((first keys) doc)]
             (for [key (rest keys)]
               ^{:key key}
               [:td.font-weight-light (key doc)])]))]]])))

(defn admin-app []
  (let [selected-doc (r/atom nil)
        json (r/atom nil)]
    (fn []
      (if (nil? @ident)
        [login]
        [:div
         [top-navi]
         [:div.container-fluid.mt-3
          (when @alert (show-alert))
          (if @selected-doc
            [document-details selected-doc json]
            [document-list selected-doc])]]))))

(defn reload! []
  (r/render [admin-app] (js/document.getElementById "app")))

(defn main! []
  (let [url (. (. js/document -location) -href)]
    (reset! backend (clojure.string/replace url #"/login" ""))
    (reload!)))
