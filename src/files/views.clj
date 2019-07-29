(ns files.views
  (:require [clojure.stacktrace]
            [clojure.pprint]))

(defn shorten
  "Cut string s at max length n and concat with '..'."
  [s n]
  (if (> (count s) n) (str (subs s 0 n) "..") s))

(defn top-navi
  "Render top bar."
  []
  [:nav {:class "navbar" :style "background-color: #990AE3;"}
   [:a {:class "navbar-brand font-weight-bold" :style "color: #ffffff;" :href "/admin"} "Files"]
   [:ul {:class "nav mr-auto"}
    [:li {:class "nav-item"}
     [:a {:class "nav-link" :style "color: #eeeeee;" :href "/swagger"} "Swagger"]]]
   [:ul {:class "nav justify-content-end"}
    [:li {:class "nav-item"}
     [:a {:class "btn btn-light" :style "colorss: #dddddd;" :href "/admin/shutdown"} "Shutdown"]]]])

(defn auditlog
  "Render auditlog."
  [logs]
  [:table {:class "table table-sm"}
   [:thead {:class "thead-light"}
    [:tr [:th "Id"] [:th "Date"] [:th "User"] [:th "Event"]]]
   [:tbody
    (for [log logs]
      [:tr [:td (:id log)] [:td (:created log)] [:td (:userid log)] [:td (:event log)]])]])

(defn details
  "Render document details page."
  [document logs]
  [:html
   [:head [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}] [:meta {:charset "UTF-8"}]]
   [:body
    (top-navi)
    [:div {:class "card mx-auto my-3" :style "width: 47rem;"}
     [:div {:class "card-header"}
      [:h3 {:class "text-center font-weight-bolder"} (:file_name document)]
      [:table {:class "table table-sm table-borderless"}
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Id:"]
        [:td (:id document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Type:"]
        [:td (:mime_type document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Category:"]
        [:td (:category document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Created:"]
        [:td (:created document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Updated:"]
        [:td (:updated document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Closed:"]
        [:td (:closed document)]]]
      [:a {:class "badge badge-primary jml-3" :href (str "/admin/download/" (:id document))} "download"]
      [:a {:class "badge badge-info ml-3" :href (str "/api/files/" (:id document))} "json"]
      [:a {:class "badge badge-danger ml-3" :href (str "/admin/close/" (:id document))} "close"]
      [:a {:class "badge badge-danger ml-3" :href (str "/admin/open/" (:id document))} "open"]]
     [:div {:class "card-body"}
      [:h4 "Metadata"]
      [:table {:class "table table-sm table-striped"}
       [:thead {:class "thead-dark"} [:tr [:th {:style "width: 220px;"} "Name"] [:th "Value"]]]
       [:tbody
        (for [key (sort (keys (:metadata document)))]
          [:tr
           [:td {:class "font-weight-bolder"} key] [:td (get (:metadata document) key)]])]]
      [:h4 (format "Latest %d auditlogs" (count logs))]
      (auditlog logs)]]]])

(defn error
  "Render error page."
  [exp msg]
  (let [trace (with-out-str (clojure.stacktrace/print-stack-trace exp 25))
        info (with-out-str (clojure.pprint/pprint (ex-data exp)))]
    [:html
     [:head [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}] [:meta {:charset "UTF-8"}]]
     [:body
      (top-navi)
      [:div {:class "card mx-auto my-3" :style "width: 55rem;"}
       [:div {:class "card-header"}
        [:h3 {:class "text-center font-weight-bolder"} msg]
        [:h5 {:class "text-center font-weight-bolder"} (.getMessage exp)]]
       [:div {:class "card-body"}
        [:h4 "Stacktrace"]
        [:pre trace]
        [:h4 "Info"]
        [:pre info]]]]]))

(defn configuration
  "Render configuration details"
  [config]
  [:div {:class "card border-light"}
   [:div {:class "card-header"} "Database Configuration"]
   [:div {:class "card-body pb-0"}
    [:pre "db: " (get-in config [:db-pool :subprotocol])
     "\nuri: " (get-in config [:db-pool :subname])
     "\npool min: " (get-in config [:db-pool :min-pool-size])
     "\npool max: " (get-in config [:db-pool :max-pool-size])]]])

(defn upload-form
  "Render document upload form"
  []
  [:div {:class "card border-light"}
   [:div {:class "card-header"} "Upload Document"]
   [:div {:class "card-body"}
    [:form {:action "/admin/upload" :method "post" :enctype "multipart/form-data"}
     [:div {:class "form-group"}
      [:input {:name "file" :type "file" :size "40"}]
      [:input {:type "submit" :name "submit" :value "submit"}]]]]])

(defn admin
  "Render admin main page."
  [documents config]
  [:html
   [:head [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}] [:meta {:charset "UTF-8"}]]
   [:script "function jump(location) {window.location = location;}"]
   [:body
    (top-navi)
    [:div {:class "container-fluid mt-3"}
     [:div {:class "row"}
      [:div {:class "col"} (configuration config)]
      [:div {:class "col"} (upload-form)]]
     [:div {:class "row text-center"}
      [:div {:class "col pt-2"} [:h5 (str "Latest " (count documents) " documents")]]]
     [:table {:class "table table-hover"}
      [:thead {:class "thead-lightdd"}
       [:tr
        [:th {:scope "col"} "Filename"]
        [:th {:scope "col"} "Type"]
        [:th {:scope "col"} "Created"]
        [:th {:scope "col"} "Updated"]
        [:th {:scope "col"} "Closed"]
        [:th {:scope "col" :class "text-right"} "File Size (kB)"]]]
      [:tbody
       (for [d documents]
         [:tr {:onclick (str "jump('/admin/details/" (:id d) "')")}
          [:th {:scope "row"} (shorten (:file_name d) 30)]
          [:td {:class "font-weight-light"} [:i (shorten (:mime_type d) 25)]]
          [:td {:class "font-weight-light"} (:created d)]
          [:td {:class "font-weight-light"} (:updated d)]
          [:td {:class "font-weight-light"} (:closed d)]
          [:td {:class "font-weight-light text-right"} (str (int (/ (:file_size d) 1000)))]])]]]]])
