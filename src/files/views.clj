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
        "\npool max: " (get-in config [:db-pool :max-pool-size])]]
      [:div {:class "col"}
       [:h4 "Upload"]
       [:form {:action "/api/files" :method "post" :enctype "multipart/form-data"}
        [:div {:class "form-group"}
         [:input {:name "file" :type "file" :size "40"}]
         [:input {:type "submit" :name "submit" :value "submit"}]]]]]
     [:div {:class "row"} [:div {:class "col"} [:h4 (str "Latest " (count files) " files")]]]
     (for [file files]
       [:div {:class "row container-fluid align-items-center"}
        [:div {:class "container row align-items-center"}
         [:div {:class "col-8"}
          [:a {:class "container row align-items-center py-2 list-group-item-action"
               :href (str  "/api/files/" (:files/id file))}
           [:div {:class "col font-weight-bolder"} (:files/file_name file)]
           [:div {:class "col-5"} (:files/created file)]
           [:div {:class "col-3 font-weight-light"} [:i (:files/mime_type file)]]]]
         [:div {:class "col"}
          [:a {:class "btn btn-sm btn-outline-danger" :href (str "/admin/delete/" (:files/id file))} "delete"]]]])]]])
