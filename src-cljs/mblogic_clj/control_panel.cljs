(ns mblogic-clj.control-panel
  "Control panel for PLC execution and monitoring."
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [clojure.string :as str]
            [mblogic-clj.server-comm :as server]))

;;; ============================================================
;;; Status Display
;;; ============================================================

(defn format-status
  "Format status object for display"
  [status]
  (str "Scan: " (:scan-count status 0)
       " | Time: " (js/Math.round (or (:scan-time status 0) 0)) "ms"
       " | Running: " (if (:running status) "Yes" "No")))

(defn update-status-display
  "Update the status display area"
  [status]
  (try
    (when-let [status-elem (dom/getElement "statusDisplay")]
      (set! (.-textContent status-elem) (format-status status)))

    (when-let [scan-elem (dom/getElement "scanCount")]
      (set! (.-textContent scan-elem) (str (:scan-count status 0))))

    (when-let [time-elem (dom/getElement "scanTime")]
      (set! (.-textContent time-elem) (str (js/Math.round (or (:scan-time status 0) 0)) "ms")))

    (when-let [running-elem (dom/getElement "runningStatus")]
      (set! (.-className running-elem)
            (if (:running status) "status-running" "status-stopped")))

    (catch js/Object e
      (.error js/console "Failed to update status display:" e))))

;;; ============================================================
;;; Data Table Display
;;; ============================================================

(defn render-address-table
  "Render a section of the data table"
  [section title addresses data]
  (try
    (let [table (dom/createDom "table" #js{:class "data-table"})]
      ;; Header
      (let [header (.appendChild table (dom/createDom "thead"))]
        (.appendChild header (dom/createDom "tr" nil
          (dom/createDom "th" nil title)
          (dom/createDom "th" nil "Value"))))

      ;; Body
      (let [tbody (.appendChild table (dom/createDom "tbody"))]
        (doseq [addr addresses]
          (let [value (get data addr "N/A")
                row (.appendChild tbody (dom/createDom "tr"))]
            (.appendChild row (dom/createDom "td" nil addr))
            (.appendChild row (dom/createDom "td" #js{:class "data-value"} (str value))))))

      table)

    (catch js/Object e
      (.error js/console "Failed to render address table:" e)
      nil)))

(defn update-data-table-display
  "Update data table display with current values"
  [data-table]
  (try
    (when-let [dt-container (dom/getElement "dataTableContainer")]
      ;; Clear existing content
      (set! (.-innerHTML dt-container) "")

      ;; Show boolean addresses (sample)
      (when-let [bool-data (:bool data-table)]
        (let [bool-table (render-address-table "Boolean" "Address"
                                               (take 10 (keys bool-data)) bool-data)]
          (when bool-table
            (.appendChild dt-container bool-table))))

      ;; Show word addresses (sample)
      (when-let [word-data (:word data-table)]
        (let [word-table (render-address-table "Word" "Address"
                                               (take 10 (keys word-data)) word-data)]
          (when word-table
            (.appendChild dt-container word-table)))))

    (catch js/Object e
      (.error js/console "Failed to update data table display:" e))))

;;; ============================================================
;;; Control Buttons
;;; ============================================================

(defn on-start-click
  "Handle start button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Starting PLC execution...")
  (server/start-execution
    (fn [response]
      (.log js/console "Execution started:" response)
      (when (.-error response)
        (.alert js/window (str "Error starting execution: " (.-message response)))))))

(defn on-stop-click
  "Handle stop button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Stopping PLC execution...")
  (server/stop-execution
    (fn [response]
      (.log js/console "Execution stopped:" response))))

(defn on-step-click
  "Handle step button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Stepping one scan...")
  (server/step-scan
    (fn [response]
      (.log js/console "Stepped:" response))))

(defn on-refresh-click
  "Handle refresh button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Refreshing data...")
  (server/fetch-status
    (fn [status]
      (update-status-display status)))
  (server/fetch-data-table
    (fn [data-table]
      (update-data-table-display data-table))))

;;; ============================================================
;;; Parameter Input
;;; ============================================================

(defn get-max-scans
  "Get max scans parameter from input"
  []
  (when-let [input (dom/getElement "maxScansInput")]
    (let [value (.-value input)]
      (if (and value (> (.-length value) 0))
        (js/parseInt value)
        nil))))

(defn get-target-scan-time
  "Get target scan time parameter from input"
  []
  (when-let [input (dom/getElement "targetScanTimeInput")]
    (let [value (.-value input)]
      (if (and value (> (.-length value) 0))
        (js/parseFloat value)
        nil))))

;;; ============================================================
;;; Control Panel Initialization
;;; ============================================================

(defn init-control-panel
  "Initialize the control panel and attach event listeners"
  []
  (try
    (.log js/console "Initializing control panel...")

    ;; Attach button listeners
    (when-let [start-btn (dom/getElement "startBtn")]
      (events/listen start-btn "click" on-start-click))

    (when-let [stop-btn (dom/getElement "stopBtn")]
      (events/listen stop-btn "click" on-stop-click))

    (when-let [step-btn (dom/getElement "stepBtn")]
      (events/listen step-btn "click" on-step-click))

    (when-let [refresh-btn (dom/getElement "refreshBtn")]
      (events/listen refresh-btn "click" on-refresh-click))

    (.log js/console "Control panel initialized")

    (catch js/Object e
      (.error js/console "Failed to initialize control panel:" e))))

;;; ============================================================
;;; Status Indicators
;;; ============================================================

(defn set-status-indicator
  "Set status indicator color and text"
  [status]
  (try
    (when-let [indicator (dom/getElement "statusIndicator")]
      (let [class-name (if (:running status) "running" "stopped")]
        (set! (.-className indicator) (str "status-indicator " class-name))))

    (catch js/Object e
      (.error js/console "Failed to set status indicator:" e))))

(defn update-progress
  "Update progress bar with scan time information"
  [status]
  (try
    (when-let [progress (dom/getElement "scanTimeProgress")]
      (let [scan-time (or (:scan-time status) 0)
            target-time 50  ; Assume 50ms target
            percent (js/Math.min 100 (/ scan-time target-time 100))]
        (set! (.-style.width progress) (str percent "%"))))

    (catch js/Object e
      (.error js/console "Failed to update progress:" e))))

;;; ============================================================
;;; Log Display
;;; ============================================================

(def log-messages (atom []))

(defn add-log-message
  "Add a message to the log display"
  [message]
  (try
    (swap! log-messages #(take 100 (cons message %)))
    (when-let [log-elem (dom/getElement "logDisplay")]
      (set! (.-textContent log-elem)
            (str/join "\n" @log-messages)))

    (catch js/Object e
      (.error js/console "Failed to add log message:" e))))

(defn clear-log
  "Clear the log display"
  []
  (reset! log-messages [])
  (when-let [log-elem (dom/getElement "logDisplay")]
    (set! (.-textContent log-elem) "")))

;;; ============================================================
;;; Keyboard Shortcuts
;;; ============================================================

(defn setup-keyboard-shortcuts
  "Setup keyboard shortcuts for common operations"
  []
  (try
    (events/listen js/document "keydown"
      (fn [evt]
        (let [key (.-key evt)
              ctrl? (.-ctrlKey evt)]
          (cond
            ;; Ctrl+S = Start
            (and ctrl? (= key "s"))
            (do (.preventDefault evt)
                (on-start-click evt))

            ;; Ctrl+T = Stop
            (and ctrl? (= key "t"))
            (do (.preventDefault evt)
                (on-stop-click evt))

            ;; Ctrl+N = Step
            (and ctrl? (= key "n"))
            (do (.preventDefault evt)
                (on-step-click evt))))))

    (.log js/console "Keyboard shortcuts initialized")

    (catch js/Object e
      (.error js/console "Failed to setup keyboard shortcuts:" e))))

;;; End of control-panel.cljs
