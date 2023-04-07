(ns bell.core
  "Functions for creating routes for ring applications."
  (:require [clojure.string :as str]))

(def ^:private not-found
  (constantly {:status 404 :headers {} :body ""}))

(defn- segs [uri]
  (filterv not-empty (str/split uri #"/")))

(defn- wrap-segs [handler]
  (fn [request]
    (if (::segs request)
      (handler request)
      (handler (assoc request ::segs (segs (:uri request)))))))

(defn- match [{:keys [method segs prefix]} request]
  (let [req-method (:request-method request)
        uri-segs (::segs request)]
    (when (and (or (= req-method method) (= method :*))
               (or prefix (<= (count uri-segs) (count segs)))
               (<= (count segs) (count uri-segs)))
      (reduce-kv (fn [params idx seg]
                   (cond
                     (str/starts-with? seg ":")
                     (assoc params (keyword (subs seg 1)) (nth uri-segs idx))

                     (str/ends-with? seg "...")
                     (if (str/starts-with? (nth uri-segs idx) (subs seg 0 (- (.length seg) 3)))
                       (reduced params)
                       (reduced nil))

                     (not= seg (nth uri-segs idx))
                     (reduced nil)

                     :else
                     params))
                 (or (:path-params request) {})
                 segs))))

(defn- match-prefix [prefix-segs {:keys [path-params] :as request}]
  (let [segs (::segs request)]
    (when-let [params (reduce-kv (fn [params idx seg]
                                   (cond
                                     (str/starts-with? seg ":")
                                     (assoc params (keyword (subs seg 1)) (nth segs idx))

                                     (not= seg (nth segs idx))
                                     (reduced nil)

                                     :else
                                     params))
                                 (or path-params {})
                                 prefix-segs)]
      {:params params
       :segs (drop (count prefix-segs) segs)})))

;; -----------------------------------------------------------------------------
;; public API

(defn route
  "Creates a route to match `method` and `pattern` to `handler`. Returns a ring
   handler that returns the result of `handler` if the request matches, and nil
   otherwise.
   
   `method` should be a keyword that matches an HTTP method, such as `:get`. It
   may also be `:*` to match any method.
   
   `pattern` is a string that describes the (potentially partial) URI for the
   request. The pattern may contain path parameters identified by keywords, such
   as `/users/:id`. In this example, `:id` is a path parameter. When a match is
   found, the route will parse the path parameters into a map attached as
   `:path-params` to the request.

   `pattern` may also be a prefix value. If a pattern ends with `/` (except the 
   root route) or `...`, the pattern is treated as a prefix. Here are some 
   examples. The pattern `/api/` matches `/api/some/other/path` and the pattern
   `/images/image-...` matches `/images/image-logo.png` but not
   `images/logo.png`.
   
   `handler` is a ring handler to handle the request if the method and pattern
   match."
  [method pattern handler]
  (let [route {:method (keyword (str/lower-case (name method)))
               :segs (segs pattern)
               :prefix (boolean (or (and (> (.length pattern) 1)
                                         (str/ends-with? pattern "/"))
                                    (str/ends-with? pattern "...")))}]
    (wrap-segs
     (fn [request]
       (when-let [params (match route request)]
         (handler (assoc request :path-params params)))))))

(defn GET
  "Creates a route for a GET call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler] (route :get pattern handler))

(defn HEAD
  "Creates a route for a HEAD call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :head pattern handler))

(defn POST
  "Creates a route for a POST call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :post pattern handler))

(defn PUT
  "Creates a route for a PUT call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :put pattern handler))

(defn PATCH
  "Creates a route for a PATCH call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :patch pattern handler))

(defn DELETE
  "Creates a route for a DELETE call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :delete pattern handler))

(defn CONNECT
  "Creates a route for a CONNECT call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :connect pattern handler))

(defn OPTIONS
  "Creates a route for a OPTIONS call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :options pattern handler))

(defn TRACE
  "Creates a route for a TRACE call to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :trace pattern handler))

(defn ANY
  "Creates a route for a call with any method to `pattern` handled by `handler`.
   
   See [[route]] for pattern rules."
  [pattern handler]
  (route :* pattern handler))

(defn group
  "Creates a group that will select among `routes` for a match. Returns nil if
   no match is found."
  [& routes]
  (wrap-segs
   (fn [request]
     (reduce (fn [_ route]
               (when-let [resp (and route (route request))]
                 (reduced resp)))
             nil
             routes))))

(defn subrouter
  "Creates a router at `prefix` and applies requests at that prefix to `routes`.
   Returns nil if no match is found. Parses path parameters from `prefix` into
   the `:path-params` field of the request."
  [prefix & routes]
  (let [prefix-segs (segs prefix)
        group-handler (apply group routes)]
    (wrap-segs
     (fn [request]
       (when-let [{:keys [params segs]} (match-prefix prefix-segs request)]
         (group-handler (assoc request ::segs segs :path-params params)))))))

(defn router
  "Creates a router that will select among `routes` for a match. Returns a 404
   Not Found response if no route matches."
  [& routes]
  (apply group (conj (vec routes) not-found)))
