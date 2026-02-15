(ns mblogic-clj.data-table
  "PLC Data Table implementation.
   Manages all address spaces: X, Y, C, SC, T, CT, DS, DD, DH, DF, TXT, etc.

   Address Spaces:
   - Boolean: X1-X2000 (inputs), Y1-Y2000 (outputs), C1-C2000 (control relays),
              SC1-SC1000 (system control), T1-T500 (timer bits), CT1-CT250 (counter bits)
   - Word: XD, YD, XS, YS (I/O registers), DS, DD, DH (data), SD, TD, CTD
   - Float: DF1-DF2000 (floating point)
   - String: TXT1-TXT10000 (text/strings)

   Ported from: src/data-table.lisp")

;;; ============================================================
;;; Data Table Structure
;;; ============================================================

(defrecord DataTable
  [bool-table    ; Atom containing map of boolean addresses
   word-table    ; Atom containing map of word addresses
   float-table   ; Atom containing map of float addresses
   string-table] ; Atom containing map of string addresses

  Object
  (toString [_]
    (format "DataTable[bool:%d word:%d float:%d string:%d]"
            (count @bool-table)
            (count @word-table)
            (count @float-table)
            (count @string-table))))

;;; ============================================================
;;; Constants for Address Ranges
;;; ============================================================

(def ^:const BOOL-RANGES
  "Address ranges for boolean values"
  {:X   [1 2000]      ; Inputs
   :Y   [1 2000]      ; Outputs
   :C   [1 2000]      ; Control relays
   :SC  [1 1000]      ; System control
   :T   [1 500]       ; Timer status
   :CT  [1 250]})     ; Counter status

(def ^:const WORD-RANGES
  "Address ranges for word (16/32-bit integer) values"
  {:XD  [1 125]       ; Input registers (unsigned)
   :YD  [1 125]       ; Output registers (unsigned)
   :XS  [1 125]       ; Input registers (signed)
   :YS  [1 125]       ; Output registers (signed)
   :DS  [1 10000]     ; Data registers (signed)
   :DD  [1 2000]      ; Data registers (double)
   :DH  [1 2000]      ; Data registers (hex/unsigned)
   :SD  [1 1000]      ; System data
   :TD  [1 500]       ; Timer current value
   :CTD [1 250]})     ; Counter current value

(def ^:const FLOAT-RANGES
  "Address ranges for floating point values"
  {:DF [1 2000]})     ; Floating point

(def ^:const STRING-RANGES
  "Address ranges for string values"
  {:TXT [1 10000]})   ; Text/strings

;;; ============================================================
;;; Address Generation
;;; ============================================================

(defn generate-addresses
  "Generate address labels from start to end with prefix.
   Example: (generate-addresses \"X\" 1 10) => [\"X1\" \"X2\" ... \"X10\"]"
  [prefix start end]
  {:pre [(string? prefix) (pos? start) (>= end start)]}
  (mapv (fn [i] (str prefix i)) (range start (inc end))))

(defn generate-all-boolean-addresses
  "Generate all boolean address labels."
  []
  (reduce (fn [acc [prefix [start end]]]
            (into acc (generate-addresses (name prefix) start end)))
          []
          BOOL-RANGES))

(defn generate-all-word-addresses
  "Generate all word address labels."
  []
  (reduce (fn [acc [prefix [start end]]]
            (into acc (generate-addresses (name prefix) start end)))
          []
          WORD-RANGES))

(defn generate-all-float-addresses
  "Generate all float address labels."
  []
  (reduce (fn [acc [prefix [start end]]]
            (into acc (generate-addresses (name prefix) start end)))
          []
          FLOAT-RANGES))

(defn generate-all-string-addresses
  "Generate all string address labels."
  []
  (reduce (fn [acc [prefix [start end]]]
            (into acc (generate-addresses (name prefix) start end)))
          []
          STRING-RANGES))

;;; ============================================================
;;; Data Table Constructor
;;; ============================================================

(defn make-data-table
  "Create a new PLC data table with all address spaces initialized to default values.
   Returns: DataTable instance with atoms for each memory space"
  []
  (let [;; Initialize boolean addresses to false
        bool-map (into {} (map vector
                              (generate-all-boolean-addresses)
                              (repeat false)))

        ;; Initialize word addresses to 0
        word-map (into {} (map vector
                              (generate-all-word-addresses)
                              (repeat 0)))

        ;; Initialize float addresses to 0.0
        float-map (into {} (map vector
                               (generate-all-float-addresses)
                               (repeat 0.0)))

        ;; Initialize string addresses to empty string
        string-map (into {} (map vector
                                (generate-all-string-addresses)
                                (repeat "")))]

    (DataTable. (atom bool-map)
                (atom word-map)
                (atom float-map)
                (atom string-map))))

;;; ============================================================
;;; Address Validation
;;; ============================================================

(defn bool-addr-p
  "Check if address is a valid boolean address."
  [address]
  {:pre [(string? address)]}
  (if-let [match (re-matches #"^([A-Z]+?)(\d+)$" address)]
    (let [[_ prefix num-str] match
          num (parse-long num-str)]
      (if-let [[start end] (get BOOL-RANGES (keyword prefix))]
        (<= start num end)
        false))
    false))

(defn word-addr-p
  "Check if address is a valid word address."
  [address]
  {:pre [(string? address)]}
  (if-let [match (re-matches #"^([A-Z]+?)(\d+)$" address)]
    (let [[_ prefix num-str] match
          num (parse-long num-str)]
      (if-let [[start end] (get WORD-RANGES (keyword prefix))]
        (<= start num end)
        false))
    false))

(defn float-addr-p
  "Check if address is a valid float address."
  [address]
  {:pre [(string? address)]}
  (if-let [match (re-matches #"^([A-Z]+?)(\d+)$" address)]
    (let [[_ prefix num-str] match
          num (parse-long num-str)]
      (if-let [[start end] (get FLOAT-RANGES (keyword prefix))]
        (<= start num end)
        false))
    false))

(defn string-addr-p
  "Check if address is a valid string address."
  [address]
  {:pre [(string? address)]}
  (if-let [match (re-matches #"^([A-Z]+?)(\d+)$" address)]
    (let [[_ prefix num-str] match
          num (parse-long num-str)]
      (if-let [[start end] (get STRING-RANGES (keyword prefix))]
        (<= start num end)
        false))
    false))

(defn any-addr-p
  "Check if address is a valid address in any space."
  [address]
  (or (bool-addr-p address)
      (word-addr-p address)
      (float-addr-p address)
      (string-addr-p address)))

;;; ============================================================
;;; Access Functions for Boolean Addresses
;;; ============================================================

(defn get-bool
  "Get boolean value at address from data table.
   Returns: boolean value or nil if address not found"
  [dt address]
  {:pre [(some? dt) (string? address)]}
  (get @(:bool-table dt) address))

(defn set-bool
  "Set boolean value at address in data table.
   Returns: updated data table (same object)"
  [dt address value]
  {:pre [(some? dt) (string? address) (boolean? value)]}
  (swap! (:bool-table dt) assoc address value)
  dt)

(defn get-bool-checked
  "Get boolean value with validation.
   Throws if address is not a valid boolean address."
  [dt address]
  {:pre [(some? dt) (string? address) (bool-addr-p address)]}
  (get-bool dt address))

(defn set-bool-checked
  "Set boolean value with validation.
   Throws if address is not a valid boolean address."
  [dt address value]
  {:pre [(some? dt) (string? address) (bool-addr-p address) (boolean? value)]}
  (set-bool dt address value))

;;; ============================================================
;;; Access Functions for Word Addresses
;;; ============================================================

(defn get-word
  "Get word (integer) value at address from data table.
   Returns: integer value or nil if address not found"
  [dt address]
  {:pre [(some? dt) (string? address)]}
  (get @(:word-table dt) address))

(defn set-word
  "Set word (integer) value at address in data table.
   Returns: updated data table (same object)"
  [dt address value]
  {:pre [(some? dt) (string? address) (integer? value)]}
  (swap! (:word-table dt) assoc address value)
  dt)

(defn get-word-checked
  "Get word value with validation.
   Throws if address is not a valid word address."
  [dt address]
  {:pre [(some? dt) (string? address) (word-addr-p address)]}
  (get-word dt address))

(defn set-word-checked
  "Set word value with validation.
   Throws if address is not a valid word address."
  [dt address value]
  {:pre [(some? dt) (string? address) (word-addr-p address) (integer? value)]}
  (set-word dt address value))

;;; ============================================================
;;; Access Functions for Float Addresses
;;; ============================================================

(defn get-float
  "Get float (double) value at address from data table.
   Returns: float value or nil if address not found"
  [dt address]
  {:pre [(some? dt) (string? address)]}
  (get @(:float-table dt) address))

(defn set-float
  "Set float (double) value at address in data table.
   Returns: updated data table (same object)"
  [dt address value]
  {:pre [(some? dt) (string? address) (number? value)]}
  (swap! (:float-table dt) assoc address (double value))
  dt)

(defn get-float-checked
  "Get float value with validation.
   Throws if address is not a valid float address."
  [dt address]
  {:pre [(some? dt) (string? address) (float-addr-p address)]}
  (get-float dt address))

(defn set-float-checked
  "Set float value with validation.
   Throws if address is not a valid float address."
  [dt address value]
  {:pre [(some? dt) (string? address) (float-addr-p address) (number? value)]}
  (set-float dt address value))

;;; ============================================================
;;; Access Functions for String Addresses
;;; ============================================================

(defn get-string
  "Get string value at address from data table.
   Returns: string value or nil if address not found"
  [dt address]
  {:pre [(some? dt) (string? address)]}
  (get @(:string-table dt) address))

(defn set-string
  "Set string value at address in data table.
   Returns: updated data table (same object)"
  [dt address value]
  {:pre [(some? dt) (string? address) (string? value)]}
  (swap! (:string-table dt) assoc address value)
  dt)

(defn get-string-checked
  "Get string value with validation.
   Throws if address is not a valid string address."
  [dt address]
  {:pre [(some? dt) (string? address) (string-addr-p address)]}
  (get-string dt address))

(defn set-string-checked
  "Set string value with validation.
   Throws if address is not a valid string address."
  [dt address value]
  {:pre [(some? dt) (string? address) (string-addr-p address) (string? value)]}
  (set-string dt address value))

;;; ============================================================
;;; Generic Value Access (Auto-detect type)
;;; ============================================================

(defn get-value
  "Get value at address, auto-detecting the type from the address prefix.
   Returns: the value (bool, int, float, or string)"
  [dt address]
  {:pre [(some? dt) (string? address)]}
  (cond
    (bool-addr-p address)   (get-bool dt address)
    (word-addr-p address)   (get-word dt address)
    (float-addr-p address)  (get-float dt address)
    (string-addr-p address) (get-string dt address)
    :else nil))

(defn set-value
  "Set value at address, auto-detecting the type from the address prefix.
   Returns: updated data table"
  [dt address value]
  {:pre [(some? dt) (string? address)]}
  (cond
    (bool-addr-p address)
    (do (assert (boolean? value) (format "Expected boolean for %s, got %s" address (type value)))
        (set-bool dt address value))

    (word-addr-p address)
    (do (assert (integer? value) (format "Expected integer for %s, got %s" address (type value)))
        (set-word dt address value))

    (float-addr-p address)
    (do (assert (number? value) (format "Expected number for %s, got %s" address (type value)))
        (set-float dt address value))

    (string-addr-p address)
    (do (assert (string? value) (format "Expected string for %s, got %s" address (type value)))
        (set-string dt address value))

    :else (throw (ex-info "Invalid address" {:address address}))))

;;; ============================================================
;;; Bulk Operations
;;; ============================================================

(defn get-bool-range
  "Get all boolean values in a range.
   Returns: map of {address value}"
  [dt prefix start end]
  {:pre [(some? dt) (string? prefix) (pos? start) (>= end start)]}
  (into {} (map (fn [addr] [addr (get-bool dt addr)])
                (generate-addresses prefix start end))))

(defn set-bool-range
  "Set boolean values for a range (all to same value).
   Returns: updated data table"
  [dt prefix start end value]
  {:pre [(some? dt) (string? prefix) (pos? start) (>= end start) (boolean? value)]}
  (doseq [addr (generate-addresses prefix start end)]
    (set-bool dt addr value))
  dt)

(defn get-word-range
  "Get all word values in a range.
   Returns: map of {address value}"
  [dt prefix start end]
  {:pre [(some? dt) (string? prefix) (pos? start) (>= end start)]}
  (into {} (map (fn [addr] [addr (get-word dt addr)])
                (generate-addresses prefix start end))))

(defn set-word-range
  "Set word values for a range (all to same value).
   Returns: updated data table"
  [dt prefix start end value]
  {:pre [(some? dt) (string? prefix) (pos? start) (>= end start) (integer? value)]}
  (doseq [addr (generate-addresses prefix start end)]
    (set-word dt addr value))
  dt)

;;; ============================================================
;;; Query Functions
;;; ============================================================

(defn count-bool-addresses
  "Count total boolean addresses initialized."
  [dt]
  {:pre [(some? dt)]}
  (count @(:bool-table dt)))

(defn count-word-addresses
  "Count total word addresses initialized."
  [dt]
  {:pre [(some? dt)]}
  (count @(:word-table dt)))

(defn count-float-addresses
  "Count total float addresses initialized."
  [dt]
  {:pre [(some? dt)]}
  (count @(:float-table dt)))

(defn count-string-addresses
  "Count total string addresses initialized."
  [dt]
  {:pre [(some? dt)]}
  (count @(:string-table dt)))

(defn total-address-count
  "Count total addresses across all spaces."
  [dt]
  {:pre [(some? dt)]}
  (+ (count-bool-addresses dt)
     (count-word-addresses dt)
     (count-float-addresses dt)
     (count-string-addresses dt)))

(defn bool-table
  "Get the complete boolean table as a map (snapshot)."
  [dt]
  {:pre [(some? dt)]}
  @(:bool-table dt))

(defn word-table
  "Get the complete word table as a map (snapshot)."
  [dt]
  {:pre [(some? dt)]}
  @(:word-table dt))

(defn float-table
  "Get the complete float table as a map (snapshot)."
  [dt]
  {:pre [(some? dt)]}
  @(:float-table dt))

(defn string-table
  "Get the complete string table as a map (snapshot)."
  [dt]
  {:pre [(some? dt)]}
  @(:string-table dt))

;;; ============================================================
;;; State Snapshot (for debugging/testing)
;;; ============================================================

(defn snapshot
  "Get a snapshot of the entire data table state.
   Returns: map with :bool :word :float :string subtables"
  [dt]
  {:pre [(some? dt)]}
  {:bool   (bool-table dt)
   :word   (word-table dt)
   :float  (float-table dt)
   :string (string-table dt)})

(defn print-summary
  "Print a summary of data table contents (for debugging)."
  [dt]
  {:pre [(some? dt)]}
  (println "Data Table Summary:")
  (println (format "  Boolean addresses: %d" (count-bool-addresses dt)))
  (println (format "  Word addresses: %d" (count-word-addresses dt)))
  (println (format "  Float addresses: %d" (count-float-addresses dt)))
  (println (format "  String addresses: %d" (count-string-addresses dt)))
  (println (format "  Total: %d" (total-address-count dt))))
