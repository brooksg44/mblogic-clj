(ns mblogic-clj.table-ops
  "Table operations for data movement.
   Implements COPY, CPYBLK, FILL, PACK, UNPACK, and search operations.
   Ported from: src/table-ops.lisp"
  (:require [mblogic-clj.data-table :as dt]
            [clojure.string :as str]))

;;; ============================================================
;;; COPY - Copy Single Value
;;; ============================================================

(defn copy-execute
  "Copy value from source address to destination address

   Parameters:
   - data-table: PLC data table
   - source-addr: Source address
   - dest-addr: Destination address

   Returns: Value copied"
  [data-table source-addr dest-addr]
  (try
    (let [value (dt/get-value data-table source-addr)]
      (dt/set-value data-table dest-addr value)
      value)
    (catch Exception _
      nil)))

;;; ============================================================
;;; CPYBLK - Copy Block
;;; ============================================================

(defn cpyblk-execute
  "Copy a block of values from source range to destination range

   Parameters:
   - data-table: PLC data table
   - source-prefix: Source address prefix (e.g., 'DS')
   - source-start: Starting index in source
   - dest-prefix: Destination address prefix (e.g., 'DD')
   - dest-start: Starting index in destination
   - count: Number of values to copy

   Returns: Number of values copied"
  [data-table source-prefix source-start dest-prefix dest-start count]
  (try
    (loop [i 0]
      (if (< i count)
        (let [src-addr (str source-prefix (+ source-start i))
              dst-addr (str dest-prefix (+ dest-start i))
              value (dt/get-value data-table src-addr)]
          (when (dt/any-addr-p dst-addr)
            (dt/set-value data-table dst-addr value))
          (recur (inc i)))
        i))
    (catch Exception _
      0)))

;;; ============================================================
;;; FILL - Fill Range with Value
;;; ============================================================

(defn fill-execute
  "Fill a range of addresses with the same value

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix (e.g., 'DS')
   - start-index: Starting index
   - count: Number of addresses to fill
   - fill-value: Value to fill with

   Returns: Number of addresses filled"
  [data-table addr-prefix start-index count fill-value]
  (try
    (loop [i 0]
      (if (< i count)
        (let [addr (str addr-prefix (+ start-index i))]
          (when (dt/any-addr-p addr)
            (dt/set-value data-table addr fill-value))
          (recur (inc i)))
        i))
    (catch Exception _
      0)))

;;; ============================================================
;;; PACK - Pack Boolean Values into Word
;;; ============================================================

(defn pack-execute
  "Pack multiple boolean values into a single word

   Each bit position corresponds to a boolean address
   Bit 0 = source[0], Bit 1 = source[1], etc.

   Parameters:
   - data-table: PLC data table
   - bool-prefix: Prefix for boolean addresses (e.g., 'X')
   - start-index: Starting index
   - word-addr: Destination word address

   Returns: Packed value"
  [data-table bool-prefix start-index word-addr]
  (try
    (let [packed-value (loop [i 0
                              value 0]
                         (if (< i 16)  ; 16-bit word
                           (let [addr (str bool-prefix (+ start-index i))
                                 bit-val (if (dt/get-value data-table addr) 1 0)
                                 new-value (+ value (bit-shift-left bit-val i))]
                             (recur (inc i) new-value))
                           value))]
      (dt/set-value data-table word-addr packed-value)
      packed-value)
    (catch Exception _
      0)))

;;; ============================================================
;;; UNPACK - Unpack Word into Boolean Values
;;; ============================================================

(defn unpack-execute
  "Unpack a word value into individual boolean addresses

   Bit 0 -> destination[0], Bit 1 -> destination[1], etc.

   Parameters:
   - data-table: PLC data table
   - word-addr: Source word address
   - bool-prefix: Prefix for boolean destinations (e.g., 'Y')
   - start-index: Starting index in destination

   Returns: Number of bits unpacked"
  [data-table word-addr bool-prefix start-index]
  (try
    (let [word-value (long (or (dt/get-word data-table word-addr) 0))]
      (loop [i 0]
        (if (< i 16)
          (let [addr (str bool-prefix (+ start-index i))
                bit-val (not (zero? (bit-and word-value (bit-shift-left 1 i))))]
            (when (dt/bool-addr-p addr)
              (dt/set-bool data-table addr bit-val))
            (recur (inc i)))
          i)))
    (catch Exception _
      0)))

;;; ============================================================
;;; Search Operations
;;; ============================================================

(defn find-equal
  "Find first occurrence of value in address range

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix (e.g., 'DS')
   - start-index: Starting search index
   - count: Number of addresses to search
   - search-value: Value to search for

   Returns: Index of first match or -1 if not found"
  [data-table addr-prefix start-index count search-value]
  (try
    (loop [i 0]
      (if (< i count)
        (let [addr (str addr-prefix (+ start-index i))
              value (dt/get-value data-table addr)]
          (if (= value search-value)
            i
            (recur (inc i))))
        -1))
    (catch Exception _
      -1)))

(defn find-greater
  "Find first occurrence where value > threshold

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting search index
   - count: Number of addresses to search
   - threshold: Threshold value

   Returns: Index of first match or -1 if not found"
  [data-table addr-prefix start-index count threshold]
  (try
    (loop [i 0]
      (if (< i count)
        (let [addr (str addr-prefix (+ start-index i))
              value (or (dt/get-word data-table addr) 0)]
          (if (> value threshold)
            i
            (recur (inc i))))
        -1))
    (catch Exception _
      -1)))

(defn find-less
  "Find first occurrence where value < threshold

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting search index
   - count: Number of addresses to search
   - threshold: Threshold value

   Returns: Index of first match or -1 if not found"
  [data-table addr-prefix start-index count threshold]
  (try
    (loop [i 0]
      (if (< i count)
        (let [addr (str addr-prefix (+ start-index i))
              value (or (dt/get-word data-table addr) 0)]
          (if (< value threshold)
            i
            (recur (inc i))))
        -1))
    (catch Exception _
      -1)))

;;; ============================================================
;;; Summary Statistics
;;; ============================================================

(defn sum-range
  "Calculate sum of values in address range

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting index
   - count: Number of addresses

   Returns: Sum of all values"
  [data-table addr-prefix start-index count]
  (try
    (reduce + 0
      (for [i (range count)]
        (or (dt/get-word data-table (str addr-prefix (+ start-index i))) 0)))
    (catch Exception _
      0)))

(defn average-range
  "Calculate average of values in address range

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting index
   - count: Number of addresses

   Returns: Average (0 if empty)"
  [data-table addr-prefix start-index count]
  (if (zero? count)
    0
    (let [total (sum-range data-table addr-prefix start-index count)]
      (/ total count))))

(defn min-range
  "Find minimum value in address range

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting index
   - count: Number of addresses

   Returns: Minimum value"
  [data-table addr-prefix start-index count]
  (try
    (if (zero? count)
      0
      (reduce min
        (for [i (range count)]
          (or (dt/get-word data-table (str addr-prefix (+ start-index i))) 0))))
    (catch Exception _
      0)))

(defn max-range
  "Find maximum value in address range

   Parameters:
   - data-table: PLC data table
   - addr-prefix: Address prefix
   - start-index: Starting index
   - count: Number of addresses

   Returns: Maximum value"
  [data-table addr-prefix start-index count]
  (try
    (if (zero? count)
      0
      (reduce max
        (for [i (range count)]
          (or (dt/get-word data-table (str addr-prefix (+ start-index i))) 0))))
    (catch Exception _
      0)))

;;; End of table-ops.clj
