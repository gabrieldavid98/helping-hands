(ns helping-hands.provider.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [helping-hands.provider.core :as core]
            [helping-hands.provider.http :refer [json]]))

(defn- get-uid
  "TODO: Integrate with Auth Service"
  [token]
  (when (and (string? token) (seq token))
    ;; validate token
    {"uid" "hhuser"}))

(def auth 
  {:name ::auth
   :enter (fn [context]
            (let [token (-> context :request :headers (get "token"))]
              (if-let [uid (and (not (nil? token)) (get-uid token))]
                (assoc-in context [:request :tx-data :user] uid)
                (chain/terminate
                 (json context :unauthorized "Auth token not found")))))
   :error core/error-handler'})

(def gen-events
  {:name ::gen-events
   :enter identity
   :error core/error-handler'})

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body `auth])

;; Tabular routes
(def routes #{["/providers/:id" 
               :get (conj common-interceptors
                          `core/validate-id
                          `core/get-provider
                          `gen-events)
               :route-name :provider-get]
              ["/providers/:id" 
               :put (conj common-interceptors
                          `core/validate-id
                          `core/upsert-provider
                          `gen-events)
               :route-name :provider-put]
              ["/providers/:id/rate" 
               :put (conj common-interceptors
                          `core/validate-id
                          `core/upsert-provider
                          `gen-events)
               :route-name :provider-rate]
              ["/providers" 
               :post (conj common-interceptors
                           `core/validate
                           `core/create-provider
                           `gen-events)
               :route-name :provider-post]
              ["/providers/:id" 
               :delete (conj common-interceptors
                             `core/validate-id
                             `core/delete-provider
                             `gen-events)
               :route-name :provider-delete]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by helping-hands.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8083
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        }})
