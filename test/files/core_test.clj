(ns files.core-test
  (:require [clojure.test :as t]
            [files.core :refer [app]]))

(t/deftest routing-test
  (t/testing "public routing"
    (t/is (= 200 (:status (app {:uri "/" :request-method :get}))))
    (t/is (= "text/html" (get-in (app {:uri "/" :request-method :get}) [:headers "Content-Type"])))
    (t/is (= 200 (:status (app {:uri "/swagger" :request-method :get}))))
    (t/is (= 200 (:status (app {:uri "/swagger.json" :request-method :get}))))
    (t/is (= 200 (:status (app {:uri "/index.html" :request-method :get})))))
  (t/testing "restricted routing"
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :delete :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files" :request-method :post :headers {}}))))
    (t/is (= true (contains? (:headers (app {:uri "/api/files" :request-method :get :headers {}})) "WWW-Authenticate")))
    (t/is (= 401 (:status (app {:uri "/api/files/somefileid" :request-method :get :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/api/files/somefileid" :request-method :delete :headers {}}))))
    (t/is (= 401 (:status (app {:uri "/admin" :request-method :get  :headers {}}))))
    (t/is (= true (contains? (:headers (app {:uri "/admin" :request-method :get :headers {}})) "WWW-Authenticate")))
    (t/is (= 401 (:status (app {:uri "/admin/shutdown" :request-method :get :headers {}}))))))
