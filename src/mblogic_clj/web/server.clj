(ns mblogic-clj.web.server
  "Web server implementation using Ring and Jetty.
   Provides HTTP endpoints for PLC control and monitoring."
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [response status]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

;;; ============================================================
;;; Global Server State
;;; ============================================================

(defonce ^:private server (atom nil))
(defonce ^:private current-config (atom nil))

;;; ============================================================
;;; HTTP Handlers
;;; ============================================================

(defn json-response [data & [status-code]]
  "Create a JSON response with proper headers"
  {:status (or status-code 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn health-handler [req]
  "Simple health check endpoint"
  (json-response {:status "ok" :message "MBLogic-CLJ Server Running"}))

(defn root-handler [req]
  "Root endpoint"
  (json-response {:name "MBLogic-CLJ"
                  :version "1.0"
                  :description "PLC Compiler/Interpreter"
                  :endpoints ["/health" "/api/status" "/api/data-table"]}))

(defn not-found-handler [req]
  "404 handler"
  (json-response {:error "Not found"
                  :path (:uri req)}
                 404))

;;; ============================================================
;;; Router
;;; ============================================================

(defn app [req]
  "Main application router"
  (let [method (:request-method req)
        path (:uri req)]
    (case [method path]
      [:get "/"] (root-handler req)
      [:get "/health"] (health-handler req)
      (not-found-handler req))))

;;; ============================================================
;;; Server Control
;;; ============================================================

(defn stop-web-server
  "Stop the running web server"
  []
  (when @server
    (try
      (log/info "Stopping web server")
      (.stop @server)
      (reset! server nil)
      (log/info "Web server stopped")
      {:status :stopped}

      (catch Exception e
        (log/error "Error stopping server:" (str e))
        {:status :error :message (str e)}))))

(defn start-web-server
  "Start the web server with the given configuration.
   Options:
   - port: HTTP port (default: 8080)"
  [& {:keys [port] :or {port 8080}}]

  (when @server
    (log/warn "Server already running, stopping first")
    (stop-web-server))

  ;; Store configuration
  (reset! current-config {:port port})

  ;; Start server
  (try
    (log/info "Starting web server on port" port)
    (reset! server (jetty/run-jetty app
                                   {:port port
                                    :join? false}))
    (log/info "Web server started successfully")
    {:status :started :port port :url (str "http://localhost:" port)}

    (catch Exception e
      (log/error "Failed to start web server:" (str e))
      {:status :error :message (str e)})))

(defn server-running?
  "Check if server is currently running"
  []
  (some? @server))

(defn get-server-url
  "Get the current server URL"
  []
  (when-let [port (:port @current-config)]
    (str "http://localhost:" port)))
