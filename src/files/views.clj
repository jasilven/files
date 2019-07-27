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
   [:a {:class "navbar font-weight-bold" :style "color: #dddddd;" :href "/admin/shutdown"} "Shutdown"]])

(defn auditlog
  "Render auditlog."
  [logs]
  [:table {:class "table table-sm"}
   [:thead {:class "thead-light"}
    [:tr [:th "Date"] [:th "User"] [:th "Event"]]]
   [:tbody
    (for [log logs]
      [:tr [:td (:auditlog/created log)] [:td (:auditlog/userid log) ] [:td (:auditlog/event log)]])]])

(defn details
  "Render document details page."
  [document logs]
  [:html
   [:head [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}] [:meta {:charset "UTF-8"}]]
   [:body
    (top-navi)
    [:div {:class "card mx-auto my-3" :style "width: 47rem;"}
     [:div {:class "card-header"}
      [:h3 {:class "text-center font-weight-bolder"} (:files/file_name document)]
      [:table {:class "table table-sm table-borderless"}
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Id:"]
        [:td (:files/id document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Type:"]
        [:td (:files/mime_type document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Category:"]
        [:td (:files/category document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Created:"]
        [:td (:files/created document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Updated:"]
        [:td (:files/updated document)]]
       [:tr [:td {:class "font-weight-bolder" :style "text-align: right"} "Deleted:"]
        [:td (:files/deleted document)]]]
      [:a {:class "badge badge-primary jml-3" :href (str "/admin/download/" (:files/id document))} "download"]
      [:a {:class "badge badge-info ml-3" :href (str "/api/files/" (:files/id document))} "json"]
      [:a {:class "badge badge-danger ml-3" :href (str "/admin/delete/" (:files/id document))} "delete"]
      [:a {:class "badge badge-danger ml-3" :href (str "/admin/undelete/" (:files/id document))} "undelete"]]
     [:div {:class "card-body"}
      [:h4 "Metadata"]
      [:table {:class "table table-sm table-striped"}
       [:thead {:class "thead-dark"} [:tr [:th {:style "width: 220px;"} "Name"] [:th "Value"]]]
       [:tbody
        (for [key (sort (keys (:files/metadata document)))]
          [:tr
           [:td {:class "font-weight-bolder"} key] [:td (get (:files/metadata document) key)]])]]
      [:h4 "Auditlog"]
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
        [:pre info]
        ]]]]))

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
      [:div {:class "col"}
       [:h4 "Database"]
       [:pre {:class "border"}
        "db: " (get-in config [:db-pool :subprotocol])
        "\nuri: " (get-in config [:db-pool :subname])
        "\npool min: " (get-in config [:db-pool :min-pool-size])
        "\npool max: " (get-in config [:db-pool :max-pool-size])]]
      [:div {:class "col"}
       [:h4 "Upload"]
       [:form {:action "/admin/upload" :method "post" :enctype "multipart/form-data"}
        [:div {:class "form-group"}
         [:input {:name "file" :type "file" :size "40"}]
         [:input {:type "submit" :name "submit" :value "submit"}]]]]]
     [:div {:class "row"} [:div {:class "col"} [:h4 (str "Latest " (count documents) " documents")]]]
     [:table {:class "table table-hover"}
      [:thead {:class "thead-light"}
       [:tr
        [:th {:scope "col"} "Filename"]
        [:th {:scope "col"} "Type"]
        [:th {:scope "col"} "Created"]
        [:th {:scope "col"} "Updated"]
        [:th {:scope "col"} "Deleted"]
        [:th {:scope "col" :class "text-right"} "File Size (KB)"]]]
      [:tbody
       (for [d documents]
         [:tr {:onclick (str "jump('/admin/details/" (:files/id d) "')")}
          [:th {:scope "row"} (shorten (:files/file_name d) 30)]
          [:td {:class "font-weight-light"} [:i (shorten (:files/mime_type d) 25)]]
          [:td {:class "font-weight-light"} (:files/created d)]
          [:td {:class "font-weight-light"} (:files/updated d)]
          [:td {:class "font-weight-light"} (:files/deleted d)]
          [:td {:class "font-weight-light text-right"} (str (int (/ (:files/file_size d) 1000)))]])]]]]])
