(ns mblogic-clj.timer-counter
  "Timer and Counter Library
   Implements TMR, TMRA, TMROFF, CNTU, CNTD, UDC instructions.
   Ported from: src/timer-counter.lisp"
  (:require [mblogic-clj.data-table :as dt]))

;;; ============================================================
;;; Timer State Management
;;; ============================================================

(defn timer-key
  "Generate key for timer state storage"
  [timer-addr suffix]
  (str timer-addr "-" suffix))

(defn timer-data-addr
  "Generate timer data address from timer bit address
   T1 -> TD1, T500 -> TD500"
  [timer-addr]
  (str "TD" (subs timer-addr 1)))

(defn counter-key
  "Generate key for counter state storage"
  [counter-addr suffix]
  (str counter-addr "-" suffix))

(defn counter-data-addr
  "Generate counter data address from counter bit address
   CT1 -> CTD1, CT250 -> CTD250"
  [counter-addr]
  (str "CTD" (subs counter-addr 2)))

;;; ============================================================
;;; Instruction Table Access
;;; ============================================================

(defn get-timer-state
  "Get accumulated time for a timer
   Returns value or 0 if not found"
  [instr-table timer-addr]
  (or (get instr-table (timer-key timer-addr "accumulated")) 0))

(defn set-timer-state
  "Store accumulated time for a timer"
  [instr-table timer-addr value]
  (assoc instr-table (timer-key timer-addr "accumulated") value))

(defn get-counter-state
  "Get count value for a counter
   Returns value or 0 if not found"
  [instr-table counter-addr]
  (or (get instr-table (counter-key counter-addr "count")) 0))

(defn set-counter-state
  "Store count value for a counter"
  [instr-table counter-addr value]
  (assoc instr-table (counter-key counter-addr "count") value))

;;; ============================================================
;;; TMR - On-Delay Timer
;;; ============================================================

(defn tmr-execute
  "Execute on-delay timer
   When enabled: accumulate time until preset is reached
   When disabled or reset: clear accumulated time

   Parameters:
   - data-table: PLC data table
   - timer-addr: Timer address (e.g., 'T1')
   - enable: Enable input (boolean)
   - preset: Preset time in milliseconds
   - scan-time: Current scan time in milliseconds

   Returns: [timer-state updated-instr-table]"
  [data-table timer-addr enable preset scan-time instr-table]
  (let [accumulated (get-timer-state instr-table timer-addr)
        td-addr (timer-data-addr timer-addr)]

    (if enable
      ;; Timer enabled - accumulate time
      (let [new-accumulated (+ accumulated scan-time)
            ;; Check if preset reached
            timer-output (>= new-accumulated preset)
            ;; Cap accumulated at preset to prevent overflow
            capped-accumulated (if timer-output preset new-accumulated)]

        ;; Update data table
        (when timer-output
          (dt/set-bool data-table timer-addr true))
        (when-not timer-output
          (dt/set-bool data-table timer-addr false))

        ;; Store accumulated time and data
        (dt/set-word data-table td-addr (long capped-accumulated))

        [timer-output (set-timer-state instr-table timer-addr capped-accumulated)])

      ;; Timer disabled - reset
      (do
        (dt/set-bool data-table timer-addr false)
        (dt/set-word data-table td-addr 0)
        [false (set-timer-state instr-table timer-addr 0)]))))

;;; ============================================================
;;; TMRA - Accumulating On-Delay Timer
;;; ============================================================

(defn tmra-execute
  "Execute accumulating on-delay timer
   Like TMR but retains accumulated time when disabled.
   Only resets when reset input is true.

   Parameters:
   - data-table: PLC data table
   - timer-addr: Timer address (e.g., 'T1')
   - enable: Enable input (boolean)
   - reset: Reset input (boolean)
   - preset: Preset time in milliseconds
   - scan-time: Current scan time in milliseconds

   Returns: [timer-state updated-instr-table]"
  [data-table timer-addr enable reset preset scan-time instr-table]
  (let [accumulated (get-timer-state instr-table timer-addr)
        td-addr (timer-data-addr timer-addr)]

    (if reset
      ;; Reset takes priority
      (do
        (dt/set-bool data-table timer-addr false)
        (dt/set-word data-table td-addr 0)
        [false (set-timer-state instr-table timer-addr 0)])

      (if enable
        ;; Timer enabled - accumulate time
        (let [new-accumulated (+ accumulated scan-time)
              ;; Check if preset reached
              timer-output (>= new-accumulated preset)
              ;; Cap accumulated at preset
              capped-accumulated (if timer-output preset new-accumulated)]

          (dt/set-bool data-table timer-addr timer-output)
          (dt/set-word data-table td-addr (long capped-accumulated))

          [timer-output (set-timer-state instr-table timer-addr capped-accumulated)])

        ;; Timer disabled - retain accumulated time (do nothing)
        (let [current-output (dt/get-bool data-table timer-addr)]
          [current-output instr-table])))))

;;; ============================================================
;;; TMROFF - Off-Delay Timer
;;; ============================================================

(defn tmroff-execute
  "Execute off-delay timer
   Output is immediately set when enabled.
   When disabled, timer starts counting and output stays on
   until preset time has elapsed.

   Parameters:
   - data-table: PLC data table
   - timer-addr: Timer address
   - enable: Enable input (boolean)
   - preset: Preset time in milliseconds
   - scan-time: Current scan time in milliseconds

   Returns: [timer-state updated-instr-table]"
  [data-table timer-addr enable preset scan-time instr-table]
  (let [accumulated (get-timer-state instr-table timer-addr)
        td-addr (timer-data-addr timer-addr)]

    (if enable
      ;; Timer enabled - output immediately ON
      (do
        (dt/set-bool data-table timer-addr true)
        (dt/set-word data-table td-addr 0)
        [true (set-timer-state instr-table timer-addr 0)])

      ;; Timer disabled - count down
      (let [new-accumulated (+ accumulated scan-time)
            ;; Output stays ON until preset time elapsed
            timer-output (< new-accumulated preset)
            ;; Cap at preset
            capped-accumulated (if timer-output new-accumulated preset)]

        (dt/set-bool data-table timer-addr timer-output)
        (dt/set-word data-table td-addr (long capped-accumulated))

        [timer-output (set-timer-state instr-table timer-addr capped-accumulated)]))))

;;; ============================================================
;;; CNTU - Count Up
;;; ============================================================

(defn cntu-execute
  "Execute count up counter
   Increments count on rising edge of input.
   Resets when reset input is true.

   Parameters:
   - data-table: PLC data table
   - counter-addr: Counter address (e.g., 'CT1')
   - enable: Enable input (boolean)
   - reset: Reset input (boolean)
   - preset: Preset count value

   Returns: [counter-state updated-instr-table]"
  [data-table counter-addr enable reset preset instr-table]
  (let [count (get-counter-state instr-table counter-addr)
        prev-enable (or (get instr-table (counter-key counter-addr "prev-enable")) false)
        ctd-addr (counter-data-addr counter-addr)]

    (if reset
      ;; Reset takes priority
      (do
        (dt/set-bool data-table counter-addr false)
        (dt/set-word data-table ctd-addr 0)
        [false (set-counter-state instr-table counter-addr 0)])

      ;; Check for rising edge (enable went from false to true)
      (let [rising-edge (and enable (not prev-enable))
            new-count (if rising-edge (inc count) count)
            ;; Check if preset reached
            counter-output (>= new-count preset)
            ;; Cap at preset
            capped-count (if counter-output preset new-count)]

        (when counter-output
          (dt/set-bool data-table counter-addr true))
        (when-not counter-output
          (dt/set-bool data-table counter-addr false))

        (dt/set-word data-table ctd-addr capped-count)

        [counter-output (->
                          instr-table
                          (set-counter-state counter-addr capped-count)
                          (assoc (counter-key counter-addr "prev-enable") enable))]))))

;;; ============================================================
;;; CNTD - Count Down
;;; ============================================================

(defn cntd-execute
  "Execute count down counter
   Decrements count on rising edge of input.
   Resets when reset input is true.

   Parameters:
   - data-table: PLC data table
   - counter-addr: Counter address
   - enable: Enable input (boolean)
   - reset: Reset input (boolean)
   - preset: Preset count value

   Returns: [counter-state updated-instr-table]"
  [data-table counter-addr enable reset preset instr-table]
  (let [count (get-counter-state instr-table counter-addr)
        prev-enable (or (get instr-table (counter-key counter-addr "prev-enable")) false)
        ctd-addr (counter-data-addr counter-addr)]

    (if reset
      ;; Load preset on reset
      (do
        (dt/set-bool data-table counter-addr false)
        (dt/set-word data-table ctd-addr preset)
        [false (set-counter-state instr-table counter-addr preset)])

      ;; Check for rising edge
      (let [rising-edge (and enable (not prev-enable))
            new-count (if rising-edge (dec count) count)
            ;; Check if count reached zero
            counter-output (<= new-count 0)
            ;; Don't go below zero
            floored-count (max 0 new-count)]

        (when counter-output
          (dt/set-bool data-table counter-addr true))
        (when-not counter-output
          (dt/set-bool data-table counter-addr false))

        (dt/set-word data-table ctd-addr floored-count)

        [counter-output (->
                          instr-table
                          (set-counter-state counter-addr floored-count)
                          (assoc (counter-key counter-addr "prev-enable") enable))]))))

;;; ============================================================
;;; UDC - Up/Down Counter
;;; ============================================================

(defn udc-execute
  "Execute up/down counter
   Up pulse increments, down pulse decrements.
   Up takes priority if both asserted.

   Parameters:
   - data-table: PLC data table
   - counter-addr: Counter address
   - up-enable: Up input (boolean)
   - down-enable: Down input (boolean)
   - reset: Reset input (boolean)
   - preset: Preset count value

   Returns: [counter-state updated-instr-table]"
  [data-table counter-addr up-enable down-enable reset preset instr-table]
  (let [count (get-counter-state instr-table counter-addr)
        prev-up (or (get instr-table (counter-key counter-addr "prev-up")) false)
        prev-down (or (get instr-table (counter-key counter-addr "prev-down")) false)
        ctd-addr (counter-data-addr counter-addr)]

    (if reset
      ;; Reset to zero
      (do
        (dt/set-bool data-table counter-addr false)
        (dt/set-word data-table ctd-addr 0)
        [false (set-counter-state instr-table counter-addr 0)])

      ;; Check for rising edges
      (let [up-edge (and up-enable (not prev-up))
            down-edge (and down-enable (not prev-down))
            ;; Up takes priority
            delta (cond
                    up-edge 1
                    down-edge -1
                    :else 0)
            new-count (+ count delta)
            ;; Clamp to reasonable range
            clamped-count (max 0 (min 65535 new-count))
            ;; Check if at preset
            counter-output (= clamped-count preset)]

        (when counter-output
          (dt/set-bool data-table counter-addr true))
        (when-not counter-output
          (dt/set-bool data-table counter-addr false))

        (dt/set-word data-table ctd-addr clamped-count)

        [counter-output (->
                          instr-table
                          (set-counter-state counter-addr clamped-count)
                          (assoc (counter-key counter-addr "prev-up") up-enable)
                          (assoc (counter-key counter-addr "prev-down") down-enable))]))))

;;; End of timer-counter.clj
