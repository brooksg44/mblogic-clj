(ns mblogic-clj.web.server
  "Web server implementation using Ring and Jetty.
   Provides HTTP endpoints for PLC control and monitoring."
  (:require [ring.adapter.jetty :as jetty]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [mblogic-clj.parser :as parser]
            [mblogic-clj.ladder-renderer :as ladder]))

;;; ============================================================
;;; Global Server State
;;; ============================================================

(defonce ^:private server (atom nil))
(defonce ^:private current-config (atom nil))
(defonce ^:private current-program (atom nil))
(defonce ^:private analyzed-networks (atom nil))

;;; ============================================================
;;; HTTP Handlers
;;; ============================================================

(defn json-response [data & [status-code]]
  "Create a JSON response with proper headers"
  {:status (or status-code 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn file-response [filepath content-type]
  "Serve a file with the specified content type"
  (let [file (io/file filepath)]
    (if (.exists file)
      {:status 200
       :headers {"Content-Type" content-type}
       :body file}
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "File not found"})))

(defn health-handler [req]
  "Simple health check endpoint"
  (json-response {:status "ok" :message "MBLogic-CLJ Server Running"}))

(defn index-handler [req]
  "Serve the web UI"
  (file-response "resources/index.html" "text/html; charset=utf-8"))

(defn api-root-handler [req]
  "API root endpoint (for API documentation)"
  (json-response {:name "MBLogic-CLJ"
                  :version "1.0"
                  :description "PLC Compiler/Interpreter"
                  :endpoints {
                    :health {:method "GET" :path "/health" :description "Health check"}
                    :load-program {:method "POST" :path "/api/load-program" :description "Load IL program"}
                    :program-summary {:method "GET" :path "/api/program-summary" :description "Get loaded program summary"}
                    :ladder {:method "GET" :path "/api/ladder/{network-id}" :description "Render network as ladder diagram"}
                  }}))

(defn not-found-handler [req]
  "404 handler"
  (json-response {:error "Not found"
                  :path (:uri req)}
                 404))

(defn load-program-handler [req]
  "Load an IL program from the test directory"
  (try
    (let [source (slurp "test/plcprog.txt")
          parsed (parser/parse-il-string source)]
      (reset! current-program parsed)
      (reset! analyzed-networks (ladder/analyze-program parsed))
      (log/info "Program loaded successfully")
      (json-response {:status "ok"
                      :message "Program loaded"
                      :networks (count @analyzed-networks)
                      :file "test/plcprog.txt"}))
    (catch Exception e
      (log/error "Failed to load program:" (str e))
      (json-response {:error (str e) :message "Failed to load program"} 500))))

(defn program-summary-handler [req]
  "Get summary of currently loaded program"
  (if @current-program
    (let [summary (ladder/render-program-summary @analyzed-networks)]
      (json-response {:status "ok"
                      :program-loaded true
                      :total-networks (count @analyzed-networks)
                      :ladder-renderability summary}))
    (json-response {:status "ok" :program-loaded false :message "No program loaded"})))

(defn ladder-renderer-handler [req]
  "Render a specific network as a ladder diagram"
  (if @current-program
    (let [network-id (some-> (:uri req)
                             (str/split #"/")
                             last
                             Integer/parseInt)]
      (if-let [rung-analyses (get @analyzed-networks network-id)]
        (let [can-render? (every? :can-render-ladder? rung-analyses)
              rendered (ladder/render-network network-id rung-analyses)]
          (json-response {:status "ok"
                          :network-id network-id
                          :can-render-ladder? can-render?
                          :instruction-count (reduce + 0 (map :instruction-count rung-analyses))
                          :svg-data (when rendered (str rendered))}))
        (json-response {:error "Network not found"} 404)))
    (json-response {:error "No program loaded"} 400)))

;;; ============================================================
;;; Router
;;; ============================================================

(defn app [req]
  "Main application router"
  (let [method (:request-method req)
        path (:uri req)]
    (case [method path]
      ;; Web UI
      [:get "/"] (index-handler req)

      ;; Health check
      [:get "/health"] (health-handler req)

      ;; API endpoints
      [:get "/api"] (api-root-handler req)
      [:post "/api/load-program"] (load-program-handler req)
      [:get "/api/program-summary"] (program-summary-handler req)

      ;; Ladder diagram rendering routes
      (cond
        (and (= method :get) (str/starts-with? path "/api/ladder/"))
        (ladder-renderer-handler req)

        :else (not-found-handler req)))))

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
