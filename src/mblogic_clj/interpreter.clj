(ns mblogic-clj.interpreter
  "PLC Interpreter - Executes compiled programs with scan-based real-time behavior."
  (:require [mblogic-clj.data-table :as dt]
            [mblogic-clj.instructions :as instr]
            [clojure.string :as str]))

;;; ============================================================
;;; PLC Runtime Error
;;; ============================================================

(defn plc-runtime-error
  "Create a PLC runtime error with context"
  [message & {:keys [subroutine network scan-number]}]
  {:error-type :plc-runtime-error
   :message message
   :subroutine subroutine
   :network network
   :scan-number scan-number})

(defn runtime-error?
  "Check if item is a runtime error"
  [item]
  (and (map? item) (= :plc-runtime-error (:error-type item))))

;;; ============================================================
;;; Scan Statistics
;;; ============================================================

(defrecord ScanStatistics
  [total-scans        ; Total scans executed
   total-time         ; Total execution time (ms)
   min-scan-time      ; Minimum scan time (ms)
   max-scan-time      ; Maximum scan time (ms)
   last-scan-time     ; Most recent scan time (ms)
   errors])           ; Number of runtime errors

(defn make-scan-statistics
  "Create new scan statistics"
  []
  (ScanStatistics. 0 0 Double/MAX_VALUE 0 0 0))

(defn update-statistics
  "Update statistics with new scan time"
  [stats scan-time-ms]
  (-> stats
      (update :total-scans inc)
      (update :total-time + scan-time-ms)
      (assoc :last-scan-time scan-time-ms)
      (update :min-scan-time min scan-time-ms)
      (update :max-scan-time max scan-time-ms)))

(defn average-scan-time
  "Calculate average scan time in milliseconds"
  [stats]
  (if (> (:total-scans stats) 0)
    (/ (:total-time stats) (:total-scans stats))
    0))

;;; ============================================================
;;; PLC Interpreter
;;; ============================================================

(defrecord PlcInterpreter
  [compiled-program       ; Compiled program (from compiler)
   data-table             ; Data table (from data-table)
   running                ; Atom: running flag (boolean)
   scan-count             ; Atom: current scan number
   scan-time              ; Atom: last scan time (ms)
   exit-code              ; Atom: exit code from last run
   call-stack             ; Atom: current call stack (list of names)
   statistics             ; Atom: scan statistics
   first-scan             ; Atom: first scan flag
   last-second-time       ; Atom: time of last one-second pulse (ms)
   one-second-pulse       ; Atom: one-second pulse state
   error-handler])        ; Error handler function or nil

(defn make-plc-interpreter
  "Create a new PLC interpreter"
  [compiled-program & {:keys [data-table error-handler]}]
  (let [dt (or data-table (dt/make-data-table))]
    (PlcInterpreter.
      compiled-program
      dt
      (atom false)
      (atom 0)
      (atom 0)
      (atom nil)
      (atom '())
      (atom (make-scan-statistics))
      (atom true)
      (atom 0)
      (atom false)
      error-handler)))

(defn interpreter-running?
  "Check if interpreter is currently running"
  [interp]
  @(:running interp))

;;; ============================================================
;;; System Control Bits
;;; ============================================================

(defn update-system-bits
  "Update system control bits before each scan"
  [interp]
  (let [dt (:data-table interp)
        current-time (System/currentTimeMillis)]

    ;; SC1 - Always ON
    (dt/set-bool dt "SC1" true)

    ;; SC2 - Always OFF
    (dt/set-bool dt "SC2" false)

    ;; SC3 - Alternating bit (toggles each scan)
    (dt/set-bool dt "SC3" (odd? @(:scan-count interp)))

    ;; SC4 - Running status
    (dt/set-bool dt "SC4" @(:running interp))

    ;; SC5 - First scan flag
    (dt/set-bool dt "SC5" @(:first-scan interp))

    ;; SC6 - One-second clock (pulse on one-second boundaries)
    (let [elapsed-ms (- current-time @(:last-second-time interp))]
      (if (>= elapsed-ms 1000)
        (do
          (reset! (:last-second-time interp) current-time)
          (reset! (:one-second-pulse interp) true)
          (dt/set-bool dt "SC6" true))
        (do
          (reset! (:one-second-pulse interp) false)
          (dt/set-bool dt "SC6" false))))

    ;; SC7 - Scan complete (cleared at start)
    (dt/set-bool dt "SC7" false)

    ;; SD1 - Scan counter (low word)
    (dt/set-word dt "SD1" (mod @(:scan-count interp) 65536))

    ;; SD2 - Scan time (last scan in ms)
    (dt/set-word dt "SD2" (long @(:scan-time interp)))

    ;; SD3 - Average scan time
    (dt/set-word dt "SD3" (long (average-scan-time @(:statistics interp))))

    ;; Clear first scan flag after first scan
    (when @(:first-scan interp)
      (reset! (:first-scan interp) false))))

(defn finalize-system-bits
  "Update system bits at end of scan"
  [interp]
  (let [dt (:data-table interp)]
    ;; SC7 - Scan complete
    (dt/set-bool dt "SC7" true)))

;;; ============================================================
;;; Error Handling
;;; ============================================================

(defn handle-runtime-error
  "Handle a runtime error during execution"
  [interp error]
  ;; Increment error count
  (swap! (:statistics interp) update :errors inc)

  ;; Call custom handler if provided
  (when (:error-handler interp)
    (try
      ((:error-handler interp) interp error)
      (catch Exception _e
        ;; Ignore errors in error handler
        nil)))

  ;; Return error object with context
  (plc-runtime-error
    (str error)
    :scan-number @(:scan-count interp)))

;;; ============================================================
;;; Scan Execution
;;; ============================================================

(defn run-scan
  "Execute one complete PLC scan cycle"
  [interp]
  (let [start-time (System/currentTimeMillis)
        program (:compiled-program interp)
        dt (:data-table interp)
        main-fn (:main-fn program)
        subroutines (:subroutines program)]

    ;; Update system bits at start of scan
    (update-system-bits interp)

    ;; Execute the main program
    (try
      (let [ctx {:data-table dt
                 :logic-stack (:logic-stack program)
                 :stacktop (:stacktop program)
                 :subroutines subroutines
                 :for-loop-state (:for-loop-state program)
                 :interp interp}]
        ;; Call main function
        (when main-fn
          (main-fn ctx)))

      (catch Exception e
        ;; Handle runtime error
        (handle-runtime-error interp e)))

    ;; Calculate scan time
    (let [end-time (System/currentTimeMillis)
          scan-time-ms (double (- end-time start-time))]

      ;; Update scan time and statistics
      (reset! (:scan-time interp) scan-time-ms)
      (swap! (:statistics interp) update-statistics scan-time-ms)

      ;; Increment scan counter
      (swap! (:scan-count interp) inc)

      ;; Finalize system bits
      (finalize-system-bits interp)

      ;; Return scan time
      scan-time-ms)))

;;; ============================================================
;;; Continuous Execution
;;; ============================================================

(defn run-continuous
  "Run PLC in continuous scan mode.
   Options:
   - max-scans: Stop after this many scans (nil = run forever)
   - target-scan-time: Target scan time in ms (nil = run as fast as possible)"
  [interp & {:keys [max-scans target-scan-time]}]

  ;; Initialize interpreter state
  (reset! (:running interp) true)
  (reset! (:first-scan interp) true)
  (reset! (:scan-count interp) 0)
  (reset! (:exit-code interp) nil)
  (reset! (:last-second-time interp) (System/currentTimeMillis))

  (try
    (loop [scan-num 0]
      (when @(:running interp)
        ;; Execute one scan
        (let [scan-time (run-scan interp)]

          ;; Check for max scans
          (when (and max-scans (>= scan-num (dec max-scans)))
            (reset! (:running interp) false)
            (reset! (:exit-code interp) :max-scans-reached))

          ;; Check for exit code from END instruction
          (when @(:exit-code interp)
            (reset! (:running interp) false))

          ;; Sleep to maintain target scan time if specified
          (when (and target-scan-time (< scan-time target-scan-time))
            (let [sleep-ms (- target-scan-time scan-time)]
              (when (> sleep-ms 0)
                (Thread/sleep (long sleep-ms)))))

          ;; Recur for next scan
          (recur (inc scan-num)))))

    (finally
      ;; Cleanup
      (reset! (:running interp) false)))

  ;; Return exit code
  @(:exit-code interp))

(defn stop-interpreter
  "Stop a running interpreter"
  [interp]
  (reset! (:running interp) false)
  (reset! (:exit-code interp) :stopped))

;;; ============================================================
;;; Single-Step Execution
;;; ============================================================

(defn step-scan
  "Execute a single scan (for debugging)"
  [interp]
  (when (zero? @(:scan-count interp))
    (reset! (:first-scan interp) true)
    (reset! (:last-second-time interp) (System/currentTimeMillis)))
  (run-scan interp))

;;; ============================================================
;;; State Inspection
;;; ============================================================

(defn print-interpreter-status
  "Print current interpreter status"
  [interp]
  (let [stats @(:statistics interp)]
    (println "\n=== PLC Interpreter Status ===")
    (println (format "Running: %s" @(:running interp)))
    (println (format "Scan count: %d" @(:scan-count interp)))
    (println (format "Last scan time: %.3f ms" @(:scan-time interp)))
    (println (format "Exit code: %s" @(:exit-code interp)))
    (println "\nStatistics:")
    (println (format "  Total scans: %d" (:total-scans stats)))
    (println (format "  Average scan time: %.3f ms" (average-scan-time stats)))
    (let [min-time (:min-scan-time stats)]
      (println (format "  Min scan time: %.3f ms" (if (= min-time Double/MAX_VALUE) 0.0 min-time))))
    (println (format "  Max scan time: %.3f ms" (:max-scan-time stats)))
    (println (format "  Errors: %d" (:errors stats)))))

(defn get-bool-value
  "Get boolean value from interpreter's data table"
  [interp address]
  (dt/get-bool (:data-table interp) address))

(defn get-word-value
  "Get word value from interpreter's data table"
  [interp address]
  (dt/get-word (:data-table interp) address))

(defn set-bool-value
  "Set boolean value in interpreter's data table"
  [interp address value]
  (dt/set-bool (:data-table interp) address value))

(defn set-word-value
  "Set word value in interpreter's data table"
  [interp address value]
  (dt/set-word (:data-table interp) address value))

(defn get-float-value
  "Get float value from interpreter's data table"
  [interp address]
  (dt/get-float (:data-table interp) address))

(defn set-float-value
  "Set float value in interpreter's data table"
  [interp address value]
  (dt/set-float (:data-table interp) address value))

(defn get-string-value
  "Get string value from interpreter's data table"
  [interp address]
  (dt/get-string (:data-table interp) address))

(defn set-string-value
  "Set string value in interpreter's data table"
  [interp address value]
  (dt/set-string (:data-table interp) address value))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn test-program
  "Test an IL program with optional inputs.
   Returns the interpreter after execution.
   Options:
   - scans: Number of scans to run (default: 1)
   - inputs: alist of (address . value)"
  [source & {:keys [scans inputs]}]
  (let [scans (or scans 1)
        ;; For now, assume source is already compiled
        ;; In practice, would need to parse and compile first
        dt (dt/make-data-table)
        compiled {:main-fn identity
                  :logic-stack (atom '())
                  :stacktop (atom false)
                  :subroutines {}
                  :for-loop-state (atom nil)}
        interp (make-plc-interpreter compiled :data-table dt)]

    ;; Set input values
    (when inputs
      (doseq [[addr value] inputs]
        (cond
          (dt/bool-addr-p addr)
          (set-bool-value interp addr value)

          (dt/word-addr-p addr)
          (set-word-value interp addr value)

          (dt/float-addr-p addr)
          (set-float-value interp addr value)

          (dt/string-addr-p addr)
          (set-string-value interp addr value))))

    ;; Run the program
    (run-continuous interp :max-scans scans)

    ;; Return interpreter for inspection
    interp))

(defn quick-test
  "Quick test: run program and check expected outputs.
   INPUTS: alist of (address . value)
   EXPECTED-OUTPUTS: alist of (address . expected-value)
   Returns true if all outputs match, false otherwise."
  [source inputs expected-outputs]
  (let [interp (test-program source :scans 1 :inputs inputs)]
    (every? (fn [[addr expected]]
              (let [actual (cond
                             (dt/bool-addr-p addr) (get-bool-value interp addr)
                             (dt/word-addr-p addr) (get-word-value interp addr)
                             :else nil)]
                (= actual expected)))
            expected-outputs)))
