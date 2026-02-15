(ns mblogic-clj.web.server
  "Web server implementation using Ring and Compojure.
   Provides HTTP endpoints for ladder diagram visualization and PLC control."
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response redirect]]
            [ring.util.http-response :as http-response]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [mblogic-clj.interpreter :as interp]
            [mblogic-clj.web.json-api :as api]
            [mblogic-clj.web.ladder-render :as ladder]))

;;; ============================================================
;;; Global Server State
;;; ============================================================

(defonce ^:private server (atom nil))
(defonce ^:private current-config (atom nil))

;;; ============================================================
;;; Request Handlers
;;; ============================================================

(defn health-check
  "Simple health check endpoint"
  [req]
  (response {:status "ok"}))

(defn get-status
  "Get interpreter and program status"
  [req]
  (let [config @current-config
        interp-instance (:interpreter config)]
    (if interp-instance
      (response {:running (interp/interpreter-running? interp-instance)
                 :scan-count @(:scan-count interp-instance)
                 :scan-time @(:scan-time interp-instance)
                 :exit-code @(:exit-code interp-instance)})
      (http-response/bad-request {:error "No interpreter loaded"}))))

(defn get-data-table
  "Get current data table state"
  [req]
  (let [config @current-config
        interp-instance (:interpreter config)]
    (if interp-instance
      (let [dt (:data-table interp-instance)]
        (response (api/data-table-to-json dt)))
      (http-response/bad-request {:error "No interpreter loaded"}))))

(defn get-ladder-diagram
  "Get ladder diagram JSON representation"
  [req]
  (let [config @current-config
        compiled-program (:compiled-program config)]
    (if compiled-program
      (response (ladder/program-to-ladder-json compiled-program))
      (http-response/bad-request {:error "No program loaded"}))))

(defn get-subroutines
  "Get list of available subroutines"
  [req]
  (let [config @current-config
        compiled-program (:compiled-program config)]
    (if compiled-program
      (response {:subroutines (keys (:subroutines compiled-program))})
      (http-response/bad-request {:error "No program loaded"}))))

(defn upload-program
  "Upload and compile a new IL program"
  [req]
  (let [body (:body req)
        source (if (string? body) body (slurp body))]
    (try
      (log/info "Uploading new IL program")
      ;; This would parse and compile the program
      ;; For now, return success
      (response {:status "uploaded" :lines (count (clojure.string/split source #"\n"))})
      (catch Exception e
        (log/error "Failed to compile program:" (str e))
        (http-response/bad-request {:error (str e)})))))

(defn control-start
  "Start PLC execution"
  [req]
  (let [config @current-config
        interp-instance (:interpreter config)
        {:keys [max-scans target-scan-time]} (:params req)]
    (if interp-instance
      (try
        (log/info "Starting PLC execution")
        (let [max-scans (when max-scans (Long/parseLong max-scans))
              target-scan-time (when target-scan-time (Double/parseDouble target-scan-time))]
          ;; Start execution in a background thread
          (future
            (interp/run-continuous interp-instance
                                   :max-scans max-scans
                                   :target-scan-time target-scan-time))
          (response {:status "started"})))
        (catch Exception e
          (log/error "Failed to start execution:" (str e))
          (http-response/bad-request {:error (str e)})))
      (http-response/bad-request {:error "No interpreter loaded"}))))

(defn control-stop
  "Stop PLC execution"
  [req]
  (let [config @current-config
        interp-instance (:interpreter config)]
    (if interp-instance
      (try
        (log/info "Stopping PLC execution")
        (interp/stop-interpreter interp-instance)
        (response {:status "stopped"})
        (catch Exception e
          (log/error "Failed to stop execution:" (str e))
          (http-response/bad-request {:error (str e)})))
      (http-response/bad-request {:error "No interpreter loaded"}))))

(defn control-step
  "Step one scan for debugging"
  [req]
  (let [config @current-config
        interp-instance (:interpreter config)]
    (if interp-instance
      (try
        (log/info "Stepping one scan")
        (let [scan-time (interp/step-scan interp-instance)]
          (response {:status "stepped" :scan-time scan-time}))
        (catch Exception e
          (log/error "Failed to step:" (str e))
          (http-response/bad-request {:error (str e)})))
      (http-response/bad-request {:error "No interpreter loaded"}))))

;;; ============================================================
;;; Routes
;;; ============================================================

(defroutes api-routes
  (GET "/api/health" [] health-check)
  (GET "/api/status" [] get-status)
  (GET "/api/data-table" [] get-data-table)
  (GET "/api/ladder" [] get-ladder-diagram)
  (GET "/api/subroutines" [] get-subroutines)
  (POST "/api/program/upload" [] upload-program)
  (POST "/api/control/start" [] control-start)
  (POST "/api/control/stop" [] control-stop)
  (POST "/api/control/step" [] control-step))

(defroutes static-routes
  ;; Serve static files from resources/static
  (route/resources "/" {:root "static"})
  ;; Default to index.html
  (GET "/" [] (io/file "resources/static/index.html")))

(defn app
  "Main application handler combining routes and middleware"
  [req]
  (let [path (:uri req)]
    (cond
      (clojure.string/starts-with? path "/api") (api-routes req)
      :else (static-routes req))))

(defn json-response
  "Wrap a response body as JSON"
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string body)})

(defn wrap-json
  "Middleware to convert map responses to JSON"
  [handler]
  (fn [req]
    (let [response (handler req)]
      (if (map? response)
        (json-response response)
        response))))

(defn wrap-middleware
  "Apply middleware to the handler"
  [handler]
  (-> handler
      wrap-json
      wrap-params))

(defn create-handler
  "Create the request handler with middleware"
  []
  (wrap-middleware app))

;;; ============================================================
;;; Server Lifecycle
;;; ============================================================

(defn start-web-server
  "Start the web server with the given configuration.
   Options:
   - port: HTTP port (default: 8080)
   - interpreter: PLC interpreter instance
   - compiled-program: Compiled IL program
   - static-dir: Static files directory (default: resources/static)"
  [& {:keys [port interpreter compiled-program static-dir]
      :or {port 8080
           static-dir "resources/static"}}]

  (when @server
    (log/warn "Server already running, stopping first")
    (stop-web-server))

  ;; Store configuration
  (reset! current-config {:interpreter interpreter
                          :compiled-program compiled-program
                          :static-dir static-dir})

  ;; Start server
  (try
    (log/info "Starting web server on port" port)
    (reset! server (jetty/run-jetty (create-handler)
                                   {:port port
                                    :join? false}))
    (log/info "Web server started successfully")
    {:status :started :port port :url (str "http://localhost:" port)}

    (catch Exception e
      (log/error "Failed to start web server:" (str e))
      {:status :error :message (str e)})))

(defn stop-web-server
  "Stop the running web server"
  []
  (when @server
    (try
      (log/info "Stopping web server")
      (.stop @server)
      (reset! server nil)
      (reset! current-config nil)
      (log/info "Web server stopped")
      {:status :stopped}
      (catch Exception e
        (log/error "Failed to stop web server:" (str e))
        {:status :error :message (str e)})))
  (when-not @server
    {:status :not-running}))

(defn server-running?
  "Check if server is currently running"
  []
  (not (nil? @server)))

(defn get-server-url
  "Get the current server URL"
  []
  (when @server
    (let [config @current-config]
      (str "http://localhost:" (or (:port config) 8080)))))

;;; End of server.clj
