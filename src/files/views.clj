(ns files.views)

(defn admin
  "render admin page"
  [files config api-uri shutdown-uri]
  [:html
   [:head
    [:link {:rel "stylesheet" :href "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"}]
    [:meta {:charset "UTF-8"}]]
   [:body
    [:nav {:class "navbar" :style "background-color: #990AE3;"}
     [:a {:class "navbar-brand font-weight-bold" :style "color: #ffffff;" :href "#"} "Files"]
     [:a {:class "navbar font-weight-bold" :style "color: #cccccc;" :href shutdown-uri} "Shutdown"]]
    [:div {:class "container-fluid mt-3"}
     [:div {:class "row"}
      [:div {:class "col"}
       [:h4 "Configuration"]
       [:pre
        "db: " (get-in config [:db-pool :subprotocol])
        "\nuri: " (get-in config [:db-pool :subname])
        "\npool min: " (get-in config [:db-pool :min-pool-size])
        "\npool max: " (get-in config [:db-pool :max-pool-size])]]
      [:div {:class "col"}
       [:h4 "Upload"]
       [:form {:action api-uri :method "post" :enctype "multipart/form-data"}
        [:div {:class "form-group"}
         [:input {:id "filesubmit" :name "file" :type "file" :size "40"}]
         [:input {:type "submit" :name "submit" :value "submit"}]]]]]
     [:div {:class "row"} [:div {:class "col"} [:h4 (str "Latest " (count files) " files")]]]
     (for [file files]
       [:div {:class "jrow"}
        [:a {:class "row list-group-item-action"
             :href (str api-uri "/" (:files/id file))}
         [:div {:class "col-2 font-weight-bolder"} (:files/file_name file)]
         [:div {:class "col-3"} (:files/created file)]
         [:div {:class "col font-weight-light"} [:i (:files/mime_type file)]]]])]]])
