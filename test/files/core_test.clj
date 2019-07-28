(ns files.core-test
  (:require [clojure.test :as t]
            [files.core :refer [app]]))

(t/deftest pub-routing
  (t/testing "public routing"
    (t/is (= 200 (:status (app {:uri "/" :request-method :get}))))
    (t/is (= "text/html" (get-in (app {:uri "/" :request-method :get}) [:headers "Content-Type"])))
    (t/is (= 200 (:status (app {:uri "/swagger" :request-method :get}))))
    (t/is (= 200 (:status (app {:uri "/swagger.json" :request-method :get}))))
    (t/is (= 200 (:status (app {:uri "/index.html" :request-method :get}))))))

(t/deftest api-auth-routing
  (t/testing "restricted routing access"
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :close :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :post :headers {}}))))
    (t/is (= true (contains? (:headers (app {:uri "/api/files" :request-method :get :headers {}})) "WWW-Authenticate")))
    (t/is (= 401 (:status (app {:uri "/api/files/somefileid" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files/somefileid" :request-method :close :headers {}}))))))

(t/deftest admin-routing
  (t/testing "admin route access"
    (t/is (= 401 (:status (app {:uri "/admin" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/admin/close/someid" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/admin/download/someid" :request-method :get :headers {}}))))
    (t/is (= true (contains? (:headers (app {:uri "/admin" :request-method :get :headers {}})) "WWW-Authenticate")))
    (t/is (= 401 (:status (app {:uri "/admin/shutdown" :request-method :get :headers {}}))))))
