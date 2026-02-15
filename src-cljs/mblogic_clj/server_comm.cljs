(ns mblogic-clj.server-comm
  "Server communication utilities for REST API calls."
  (:require [goog.net.XhrIo :as xhr]
            [goog.json :as json]
            [goog.events :as events]
            [clojure.string :as str]))

;;; ============================================================
;;; HTTP Utilities
;;; ============================================================

(defn make-request
  "Make an HTTP request and call callback with parsed JSON response"
  [method url callback & {:keys [body error-callback]}]
  (let [request (xhr/send
                  url
                  (fn [evt]
                    (let [resp (.-target evt)
                          status (.getStatus resp)
                          response-text (.getResponseText resp)]
                      (if (= status 200)
                        (try
                          (let [data (json/parse response-text)]
                            (callback data))
                          (catch js/Object e
                            (.error js/console "Failed to parse JSON:" e)
                            (when error-callback (error-callback e))))
                        (do
                          (.error js/console (str "HTTP " status ": " response-text))
                          (when error-callback (error-callback response-text))))))
                  method
                  body)]
    request))

;;; ============================================================
;;; API Endpoints
;;; ============================================================

(defn fetch-status
  "Fetch interpreter status from API"
  [callback]
  (make-request "GET" "/api/status" callback))

(defn fetch-data-table
  "Fetch complete data table from API"
  [callback]
  (make-request "GET" "/api/data-table" callback))

(defn fetch-ladder
  "Fetch ladder diagram JSON for a subroutine
   subrname: 'main' for main program, or subroutine name"
  [subrname callback]
  (let [url (str "/api/ladder?subrname=" (js/encodeURIComponent subrname))]
    (make-request "GET" url callback)))

(defn fetch-subroutines
  "Fetch list of available subroutines"
  [callback]
  (make-request "GET" "/api/subroutines"
    (fn [data]
      (let [subs (if (array? (.-subroutines data))
                   (array-seq (.-subroutines data))
                   [])]
        (callback subs)))))

(defn fetch-program-info
  "Fetch program metadata"
  [callback]
  (make-request "GET" "/api/program" callback))

;;; ============================================================
;;; Control Commands
;;; ============================================================

(defn start-execution
  "Start PLC execution
   Options: max-scans, target-scan-time (in ms)"
  [callback & {:keys [max-scans target-scan-time]}]
  (let [params (str "?"
                    (when max-scans (str "max-scans=" max-scans))
                    (when target-scan-time
                      (str (when max-scans "&") "target-scan-time=" target-scan-time)))]
    (make-request "POST" (str "/api/control/start" params) callback)))

(defn stop-execution
  "Stop PLC execution"
  [callback]
  (make-request "POST" "/api/control/stop" callback))

(defn step-scan
  "Execute one scan step for debugging"
  [callback]
  (make-request "POST" "/api/control/step" callback))

;;; ============================================================
;;; Data Access
;;; ============================================================

(defn get-address-value
  "Get value of a single address
   Type is auto-detected from address prefix"
  [address callback]
  (let [url (str "/api/data-table?address=" (js/encodeURIComponent address))]
    (make-request "GET" url callback)))

(defn get-address-info
  "Get information about an address (type, validity)"
  [address callback]
  (let [url (str "/api/address-info?address=" (js/encodeURIComponent address))]
    (make-request "GET" url callback)))

(defn list-addresses
  "Get all addresses of a given type"
  [type callback]
  (let [url (str "/api/addresses?type=" type)]
    (make-request "GET" url callback)))

;;; ============================================================
;;; Polling Helpers
;;; ============================================================

(defn create-poller
  "Create a polling function that calls callback at interval
   interval: milliseconds between calls
   fn: function to call (no arguments)"
  [fn interval]
  (.setInterval js/window fn interval))

(defn stop-poller
  "Stop a polling function created by create-poller
   poll-id: return value from create-poller"
  [poll-id]
  (.clearInterval js/window poll-id))

;;; ============================================================
;;; Error Handling
;;; ============================================================

(defn handle-error
  "Generic error handler for API calls"
  [error]
  (.error js/console (str "API error: " error)))

(defn retry-after-delay
  "Retry a function after a delay
   fn: function to call
   delay-ms: milliseconds to wait"
  [fn delay-ms]
  (.setTimeout js/window fn delay-ms))

;;; End of server-comm.cljs
