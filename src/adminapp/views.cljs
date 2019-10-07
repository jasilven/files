(ns adminapp.views
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [re-frame.core :as rf :refer [subscribe dispatch]]
            [clojure.string :as str]))

(defn uri
  "Return backend uri without any path."
  [path]
  (str @(rf/subscribe [:backend]) path))

(defn json->clj [json]
  (js->clj json :keywordize-keys true))

(defn clj->json [ds pretty?]
  (if pretty?
    (.stringify js/JSON (clj->js ds) nil 2)
    (.stringify js/JSON (clj->js ds))))

(defn send-to-browser
  "Opens browser generated file in browser."
  [binary filename mimetype]
  (let [data-blob (js/Blob. #js [binary] #js {:type mimetype})
        link (.createElement js/document "a")]
    (set! (.-href link) (.createObjectURL js/URL data-blob))
    (.setAttribute link "download" filename)
    (.appendChild (.-body js/document) link)
    (.click link)
    (.removeChild (.-body js/document) link)))

(defn download
  "Download 'doc' from server and push it to the browser."
  [doc]
  (let [token @(rf/subscribe [:token])
        path (str "/api/files/" (:id doc) "/download")]
    (go (let [response (<! (http/get (uri path) {:oauth-token token
                                                 :response-type :array-buffer}))]
          (if (= 200 (:status response))
            (send-to-browser (:body response) (:filename doc) (:mimetype doc))
            (rf/dispatch [:alert "Document download error"]))))))

(defn spinner []
  [:div.text-center.spinner-border.spinner-border-sm {:role "status"}
   [:span.sr-only "loading.."]])

(defn search-panel []
  (let [id (reagent/atom "")]
    (fn []
      [:li.nav-item>div.input-group.input-group-sm
       [:input.form-control.mt-1 {:type "text" :placeholder "<Document ID>"
                                  :value @id :on-change #(reset! id (-> % .-target .-value))}]
       [:button.btn.btn-light.ml-1.mt-1.btn-sm {:style {:color "#444444"}
                                                :on-click #(do (rf/dispatch [:fetch-document @id])
                                                               (reset! id ""))} "Find"]])))

(defn top-navi []
  (let [refreshing? @(rf/subscribe [:refreshing?])
        backend @(rf/subscribe [:backend])
        identity @(rf/subscribe [:identity])
        page @(rf/subscribe [:active-page])]
    [:nav.navbar {:style {:background-color "#990AE3"}}
     [:ul.nav
      [:li.nav-item>a.nav-link.font-weight-bold {:style {:color "#ffffff"}} "Files"]
      [:li.nav-item>a.nav-link.text-bolder {:style {:color "#eeeeee"} :href (str backend "/swagger") :target "_blank"} "Info"]
      [:li.nav-item>a.nav-link.text-white-50.font-italic backend]
      (when identity [search-panel])]
     (when identity
       [:ul.nav.justify-content-end
        (when (= page :document-list)
          (if refreshing?
            [:li.nav-item>a.nav-link {:style {:color "#ffffff"}} [spinner]]
            [:li.nav-item>a.nav-link {:style {:color "#eeeeee"}
                                      :on-click #(rf/dispatch [:refresh-documents])} "Refresh"]))
        [:li.nav-item>a.nav-link.font-weight-bolder
         {:style {:color "#eeeeee"}} (str "🎗") [:span.font-italic.ml-1 (:user identity)]]
        [:li.nav-item>a.nav-link.font-weight-bolder
         {:style {:color "#4A0C6A"} :on-click #(rf/dispatch [:logout])} "Quit"]])]))

(defn login-page []
  (let [token (reagent/atom "")]
    (fn []
      (let [submit-fn (fn [_] (rf/dispatch [:login @token])
                        (reset! token ""))]
        [:div.card.mx-auto.mt-5 {:style {:width "800px"}}
         [:div.card-header "Sign in with token"]
         [:div.card-body
          [:input.mb-2.form-control
           {:type "text" :value @token :placeholder "<token here>"
            :on-change #(reset! token (-> % .-target .-value))
            :on-key-press #(when (= (.-key %) "Enter") (submit-fn %))}]
          [:input {:type "button" :value "Login" :on-click #(submit-fn %)}]]]))))

(defn json-panel [json]
  [:div.card-body
   [:h4 "Json"]
   [:pre.pre-scrollable @json]
   [:button.btn.btn-sm.btn-dark.ml-0
    {:type "button"
     :on-click #(send-to-browser @json "data.json" "application/json")} "Open"]
   [:button.btn.btn-sm.btn-outline-dark.ml-2
    {:type "button" :on-click #(reset! json nil)} "✖ Close"]])

(defn logs-panel [logs]
  [:div.card-body
   [:h4 "Logs"]
   [:pre.pre-scrollable
    (doall
     (for [log logs]
       (str (:created log) "," (:event log) "," (:userid log) "," (:origin log) "\n")))]
   [:button.btn.btn-sm.btn-outline-dark.ml-2
    {:type "button" :on-click #(rf/dispatch [:clear-logs])} "✖ Close"]])

(defn metadata-panel [metadata]
  [:div.card-body
   [:h4 "Metadata"]
   [:table.table.table-sm.table-striped>tbody.small
    (doall
     (for [key (sort (keys metadata))]
       ^{:key key}
       [:tr [:td.font-weight-bolder key] [:td (get metadata key)]]))]])

(defn document-details-page []
  (let [json (reagent/atom nil)]
    (fn []
      (let [document @(rf/subscribe [:selected-document])
            logs @(rf/subscribe [:selected-document-logs])]
        [:div.card.mx-auto.my-3 {:style {:width "47rem"}}
         [:div.card-header
          [:button.btn.btn-sm.btn-outline-dark.ml-2
           {:type "button" :on-click #(rf/dispatch [:close-details])} "✖ Close"]
          [:h3.text-center.font-weight-bolder (:filename document)]
          [:table.table-sm.table-borderless>tbody
           (doall
            (for [[key val] (sort-by first (seq document))
                  :when (and (some? val) (not= key :metadata))
                  :let [key (str (str/capitalize (name key)) ":")]]
              ^{:key key}
              [:tr
               [:td.font-weight-bolder {:style {:text-align "right"}} key]
               (if (= key "Filename:")
                 [:td.btn-link.button {:type "button" :on-click #(download document)} val]
                 [:td val])]))]
          (cond
            (and (nil? @json) (nil? logs))
            [:div
             [:div.mt-3.button.btn.btn-sm.btn-dark.ml-2
              {:type "button"
               :on-click #(reset! json (clj->json document true))} "Json"]
             [:div.mt-3.button.btn.btn-sm.btn-dark.ml-2
              {:type "button"
               :on-click #(rf/dispatch [:fetch-logs (:id document)])} "Logs"]
             [metadata-panel (:metadata document)]]

            @json
            [json-panel json]

            logs
            [logs-panel logs])]]))))

(defn document-list [page page-size keys]
  (let [documents @(rf/subscribe [:documents])
        doc-cnt (count documents)
        page-first (* page-size @page)]
    (doall
     (for [index (range page-first (min doc-cnt (+ page-first page-size)))
           :let [doc (nth documents index)]]
       ^{:key (:id doc)}
       [:tr {:on-click #(rf/dispatch [:show-details doc])}
        [:th {:scope "row"} ((first keys) doc)]
        (for [key (rest keys)]
          ^{:key key}
          [:td.font-weight-light.small (key doc)])]))))

(defn pagination [page page-size]
  (fn []
    (let [documents @(rf/subscribe [:documents])
          documents-count (count documents)
          page-count (Math/ceil (/ documents-count page-size))]
      [:div.mt-3.small
       [:nav>ul.pagination.justify-content-center
        (if (> @page 0)
          [:li.page-item>a.page-link {:href "#" :on-click (fn [_] (swap! page dec))} "Prev" ]
          [:li.page-item.disabled>a.page-link {:href "#"} "Prev" ])
        [:li.page-item.disabled>a.page-link (str (inc @page) "/" page-count)]
        (if (< @page (dec page-count))
          [:li.page-item>a.page-link {:href "#" :on-click (fn [_] (swap! page inc))} "Next"]
          [:li.page-item.disabled>a.page-link {:href "#" } "Next"])]])))

(defn document-list-page []
  (let [page (reagent/atom 0)]
    (fn []
      (let [documents @(rf/subscribe [:documents])
            documents-count (count documents)
            refreshing? @(rf/subscribe [:refreshing?])
            page-size 12]
        (if-not (empty? documents)
          (let [keys [:filename :created :updated :closed]]
            [:div
             [:div.row
              [:div.align-self-center.col.justify-content-start.ml-2 [:h5 "Latest " documents-count " documents"]]
              [:div.d-flex.col.justify-content-end.mr-2 [pagination page page-size]]]
             [:table.table.table-hover
              [:thead.thead-light>tr
               (doall
                (for [key keys]
                  ^{:key key} [:th {:scope "col"} (str/capitalize (name key))]))]
              [:tbody (document-list page page-size keys)]]])
          (when-not refreshing? [:div.text-center.h5.mt-3 "No documents found"]))))))

(defn alert-panel [msg]
  [:div.alert.alert-warning.w-50.mt-2.mx-auto
   {:role "alert" :on-click #(rf/dispatch [:clear-alert])} msg])
