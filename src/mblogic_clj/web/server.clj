(ns mblogic-clj.web.server
  "Web server implementation using Ring and Jetty.
   Provides HTTP endpoints for PLC control and monitoring."
  (:require [ring.adapter.jetty :as jetty]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [mblogic-clj.parser :as parser]
            [mblogic-clj.web.ladder-render :as ladder]
            [mblogic-clj.web.json-api :as api]))

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
                  :description "PLC Compiler/Interpreter with Ladder Rendering"
                  :endpoints {
                    :health {:method "GET" :path "/health" :description "Health check"}
                    :load-program {:method "POST" :path "/api/load-program" :description "Load IL program from test/plcprog.txt"}
                    :program-summary {:method "GET" :path "/api/program-summary" :description "Get loaded program summary"}
                    :ladder {:method "GET" :path "/api/ladder/{network-id}" :description "Render network as ladder diagram (legacy)"}
                    :ladder-js {:method "GET" :path "/api/ladder-js?subrname=X" :description "Get ladder JSON for subroutine"}
                    :subroutines {:method "GET" :path "/api/subroutines" :description "List all subroutine names"}
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
          parsed (parser/parse-il-string source)
          main-networks (:main-networks parsed)
          subroutines (keys (:subroutines parsed))]
      (reset! current-program parsed)
      (log/info "Program loaded successfully")
      (json-response {:status "ok"
                      :message "Program loaded"
                      :main-networks (count main-networks)
                      :subroutines subroutines
                      :total-subroutines (count subroutines)
                      :file "test/plcprog.txt"}))
    (catch Exception e
      (log/error "Failed to load program:" (str e))
      (json-response {:error (str e) :message "Failed to load program"} 500))))

(defn program-summary-handler [req]
  "Get summary of currently loaded program"
  (if @current-program
    (let [main-networks (:main-networks @current-program)
          subroutines (keys (:subroutines @current-program))]
      (json-response {:status "ok"
                      :program-loaded true
                      :main-networks (count main-networks)
                      :subroutines subroutines
                      :total-subroutines (count subroutines)}))
    (json-response {:status "ok" :program-loaded false :message "No program loaded"})))

(defn ladder-js-handler [req]
  "Get ladder JSON for a specific subroutine.
   Query parameter: subrname=X (default: 'main')"
  (if @current-program
    (let [query-string (:query-string req)
          params (if query-string
                   (into {}
                     (map (fn [pair]
                            (let [[k v] (str/split pair #"=")]
                              [(str/lower-case k) (java.net.URLDecoder/decode v "UTF-8")]))
                          (str/split query-string #"&")))
                   {})
          subrname (get params "subrname" "main")]
      (try
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (api/get-ladder-for-subroutine @current-program subrname)}
        (catch Exception e
          (log/error "Error generating ladder JSON:" (str e))
          (json-response {:error (str e)} 500))))
    (json-response {:error "No program loaded"} 400)))

(defn subroutines-handler [req]
  "List all subroutine names in the loaded program"
  (if @current-program
    (try
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (api/get-subroutine-list @current-program)}
      (catch Exception e
        (log/error "Error getting subroutine list:" (str e))
        (json-response {:error (str e)} 500)))
    (json-response {:error "No program loaded"} 400)))

(defn ladder-renderer-handler [req]
  "Render a specific network as a ladder diagram (legacy endpoint)"
  (if @current-program
    (let [network-id (some-> (:uri req)
                             (str/split #"/")
                             last
                             Integer/parseInt)]
      (try
        (let [ladder-prog (ladder/program-to-ladder @current-program :name "main")]
          (if-let [rung (first (filter #(= (:number %) network-id) (:rungs ladder-prog)))]
            (json-response {:status "ok"
                            :network-id network-id
                            :cells (count (:cells rung))
                            :rows (:rows rung)
                            :cols (:cols rung)
                            :addresses (:addresses rung)})
            (json-response {:error "Network not found"} 404)))
        (catch Exception e
          (log/error "Error rendering network:" (str e))
          (json-response {:error (str e)} 500))))
    (json-response {:error "No program loaded"} 400)))

;;; ============================================================
;;; Router
;;; ============================================================

(defn app [req]
  "Main application router"
  (let [method (:request-method req)
        path (:uri req)]
    (cond
      ;; Web UI
      (and (= method :get) (= path "/"))
      (index-handler req)

      ;; Health check
      (and (= method :get) (= path "/health"))
      (health-handler req)

      ;; API endpoints
      (and (= method :get) (= path "/api"))
      (api-root-handler req)

      (and (= method :post) (= path "/api/load-program"))
      (load-program-handler req)

      (and (= method :get) (= path "/api/program-summary"))
      (program-summary-handler req)

      ;; Ladder diagram rendering routes
      (and (= method :get) (str/starts-with? path "/api/ladder-js"))
      (ladder-js-handler req)

      (and (= method :get) (= path "/api/subroutines"))
      (subroutines-handler req)

      (and (= method :get) (str/starts-with? path "/api/ladder/"))
      (ladder-renderer-handler req)

      ;; Default 404
      :else
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
