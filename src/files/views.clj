(ns files.views)

(defn admin
  "render admin page"
  [files config]
  [:html
   [:head [:link {:rel "stylesheet" :href "/css/bootstrap.min.css"}] [:meta {:charset "UTF-8"}]]
   [:body
    [:nav {:class "navbar" :style "background-color: #990AE3;"}
     [:a {:class "navbar-brand font-weight-bold" :style "color: #ffffff;" :href "#"} "Files"]
     [:a {:class "navbar font-weight-bold" :style "color: #dddddd;" :href "/admin/shutdown"} "Shutdown"]]
    [:div {:class "container-fluid mt-3"}
     [:div {:class "row"}
      [:div {:class "col"}
       [:h4 "Configuration"]
       [:pre {:class "border"}
        "db: " (get-in config [:db-pool :subprotocol])
        "\nuri: " (get-in config [:db-pool :subname])
        "\npool min: " (get-in config [:db-pool :min-pool-size])
        "\npool max: " (get-in config [:db-pool :max-pool-size])]]]
     [:div {:class "row"} [:div {:class "col"} [:h4 (str "Latest " (count files) " files")]]]
     [:table {:class "table"}
      [:thead
       [:tr
        [:th {:scope "col"} "Filename"]
        [:th {:scope "col"} "Type"]
        [:th {:scope "col"} "Created"]
        [:th {:scope "col"} "Updated"]
        [:th {:scope "col"} "Actions"]]]
      [:tbody
       (for [file files]
         [:tr
          [:th {:scope "row"} [:a {:class "font-weight-bolder align-items-center py-2 list-group-item-action"
                                   :href (str  "/api/files/" (:files/id file))} (:files/file_name file)]]
          [:td {:class "font-weight-light"} [:i (:files/mime_type file)]]
          [:td {:class "font-weight-light"} (:files/created file)]
          [:td {:class "font-weight-light"} (:files/updated file)]
          [:td
           [:a {:class "btn btn-sm btn-outline-primary mr-2" :href (str "/api/files/" (:files/id file))} "JSON"]
           [:a {:class "btn btn-sm btn-outline-primary mr-2" :href (str "/admin/download/" (:files/id file))} "Download"]
           [:a {:class "btn btn-sm btn-outline-danger mr-2" :href (str "/admin/delete/" (:files/id file))} "Delete"]]])]]]]])
