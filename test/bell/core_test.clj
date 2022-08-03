(ns bell.core-test
  (:require [bell.core :as bell]
            [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]))

;; -----------------------------------------------------------------------------
;; route tests

(deftest route-match
  (let [handler (bell/GET "/path" (constantly "simple"))
        req (mock/request :get "/path")]
    (is (= "simple" (handler req)))))

(deftest route-no-match
  (let [handler (bell/GET "/path" identity)
        req (mock/request :get "/users")]
    (is (nil? (handler req)))))

(deftest route-method-mismatch
  (let [handler (bell/POST "/path" identity)
        req (mock/request :get "/path")]
    (is (nil? (handler req)))))

(deftest route-root
  (let [handler (bell/OPTIONS "/" (constantly "options"))
        req (mock/request :options "/")]
    (is (= "options" (handler req)))))

(deftest route-parse-params
  (let [handler (bell/GET "/users/:id/name/:name" :path-params)
        req (mock/request :get "/users/1234/name/jeff")]
    (is (= {:id "1234" :name "jeff"} (handler req)))))

(deftest route-slash-prefix
  (let [handler (bell/GET "/images/" (constantly "image"))
        req (mock/request :get "/images/my-image.jpg")]
    (is (= "image" (handler req)))))

(deftest route-ellipsis-prefix
  (let [handler (bell/GET "/images/image-..." (constantly "image"))
        req (mock/request :get "/images/image-1234")]
    (is (= "image" (handler req)))))

(deftest route-ellipsis-prefix--no-match
  (let [handler (bell/GET "/images/image-..." (constantly "image"))
        req (mock/request :get "/images/image1234")]
    (is (nil? (handler req)))))

(deftest route-wildcard-method
  (let [handler (bell/ANY "/some/path" (constantly true))]
    (is (true? (handler (mock/request :get "/some/path"))))
    (is (true? (handler (mock/request :post "/some/path"))))))

;; -----------------------------------------------------------------------------
;; router tests

(deftest router-match
  (let [handler (bell/router
                 (bell/GET "/health" (constantly "health"))
                 (bell/subrouter
                  "/api/resource"
                  (bell/GET "/:id" :path-params))
                 (bell/subrouter
                  "/api/person/:id"
                  (bell/GET "/name/:name" :path-params)))
        req (mock/request :get "/api/person/1234/name/fred")]
    (is (= {:id "1234" :name "fred"} (handler req)))))

(deftest router-no-match
  (let [handler (bell/router
                 (bell/GET "/health" (constantly "health")))
        req (mock/request :get "/api/person/1234")]
    (is (= {:status 404 :headers {} :body ""} (handler req)))))

(deftest router-no-match-with-not-found
  (let [handler (bell/router
                 (bell/GET "/health" (constantly "health"))
                 (constantly "not found"))
        req (mock/request :get "/api/person/1234")]
    (is (= "not found" (handler req)))))

(deftest subrouter-no-params
  (let [handler (bell/subrouter "/api/person"
                                (bell/GET "/:id" :path-params))
        req (mock/request :get "/api/person/1234")]
    (is (= {:id "1234"} (handler req)))))

;; -----------------------------------------------------------------------------

(deftest helpers
  (let [handler (bell/router
                 (bell/GET "/" (constantly "get"))
                 (bell/HEAD "/" (constantly "head"))
                 (bell/POST "/" (constantly "post"))
                 (bell/PUT "/" (constantly "put"))
                 (bell/PATCH "/" (constantly "patch"))
                 (bell/DELETE "/" (constantly "delete"))
                 (bell/CONNECT "/" (constantly "connect"))
                 (bell/OPTIONS "/" (constantly "options"))
                 (bell/TRACE "/" (constantly "trace"))
                 (constantly "not-found"))]
    (is (= "get" (handler (mock/request :get "/"))))
    (is (= "head" (handler (mock/request :head "/"))))
    (is (= "post" (handler (mock/request :post "/"))))
    (is (= "put" (handler (mock/request :put "/"))))
    (is (= "patch" (handler (mock/request :patch "/"))))
    (is (= "delete" (handler (mock/request :delete "/"))))
    (is (= "connect" (handler (mock/request :connect "/"))))
    (is (= "options" (handler (mock/request :options "/"))))
    (is (= "trace" (handler (mock/request :trace "/"))))
    (is (= "not-found" (handler (mock/request :wrong "/"))))))