(ns mblogic-clj.web.json-api
  "JSON API endpoints and response generation.
   Provides JSON conversion for program state, data table, program metadata, and ladder diagrams."
  (:require [mblogic-clj.data-table :as dt]
            [mblogic-clj.instructions :as instr]
            [mblogic-clj.web.ladder-render :as ladder]
            [cheshire.core :as json]
            [clojure.string :as str]))

;;; ============================================================
;;; Data Table JSON Conversion
;;; ============================================================

(defn bool-table-to-json
  "Convert boolean table addresses and values to JSON"
  [bool-table]
  (try
    (let [addresses (dt/generate-all-boolean-addresses)]
      (into {}
            (map (fn [addr]
                   [addr (dt/get-bool-checked bool-table addr)])
                 addresses)))
    (catch Exception _
      {})))

(defn word-table-to-json
  "Convert word table addresses and values to JSON"
  [word-table]
  (try
    (let [addresses (dt/generate-all-word-addresses)]
      (into {}
            (map (fn [addr]
                   [addr (dt/get-word-checked word-table addr)])
                 addresses)))
    (catch Exception _
      {})))

(defn float-table-to-json
  "Convert float table addresses and values to JSON"
  [float-table]
  (try
    (let [addresses (dt/generate-all-float-addresses)]
      (into {}
            (map (fn [addr]
                   [addr (dt/get-float-checked float-table addr)])
                 addresses)))
    (catch Exception _
      {})))

(defn string-table-to-json
  "Convert string table addresses and values to JSON"
  [string-table]
  (try
    (let [addresses (dt/generate-all-string-addresses)]
      (into {}
            (map (fn [addr]
                   [addr (dt/get-string-checked string-table addr)])
                 addresses)))
    (catch Exception _
      {})))

(defn data-table-to-json
  "Convert entire data table to JSON representation.
   Returns: map with :bool, :word, :float, :string keys"
  [data-table]
  (try
    {:bool (bool-table-to-json @(:bool-table data-table))
     :word (word-table-to-json @(:word-table data-table))
     :float (float-table-to-json @(:float-table data-table))
     :string (string-table-to-json @(:string-table data-table))}
    (catch Exception e
      {:error (str "Failed to convert data table: " e)})))

;;; ============================================================
;;; Program Metadata JSON
;;; ============================================================

(defn subroutines-to-json
  "Convert subroutines map to JSON representation"
  [subroutines]
  (try
    (into {}
          (map (fn [[name _sbr]]
                 [name {:name name}])
               subroutines))
    (catch Exception _
      {})))

(defn program-metadata-to-json
  "Convert program metadata to JSON"
  [program]
  (try
    {:main-networks (count (:main-networks program))
     :subroutines (subroutines-to-json (:subroutines program))}
    (catch Exception e
      {:error (str "Failed to convert program metadata: " e)})))

(defn program-to-json
  "Convert compiled program to JSON representation.
   Returns: map with program metadata and structure"
  [program]
  (program-metadata-to-json program))

;;; ============================================================
;;; Address Metadata JSON
;;; ============================================================

(defn get-address-info
  "Get information about a specific address"
  [address]
  (try
    {:address address
     :valid? (or (dt/bool-addr-p address)
                 (dt/word-addr-p address)
                 (dt/float-addr-p address)
                 (dt/string-addr-p address))
     :type (cond
             (dt/bool-addr-p address) "boolean"
             (dt/word-addr-p address) "word"
             (dt/float-addr-p address) "float"
             (dt/string-addr-p address) "string"
             :else "unknown")}
    (catch Exception e
      {:address address :error (str e)})))

(defn list-addresses-by-type
  "Get all addresses for a given type"
  [type]
  (try
    (case type
      "bool" (dt/generate-all-boolean-addresses)
      "word" (dt/generate-all-word-addresses)
      "float" (dt/generate-all-float-addresses)
      "string" (dt/generate-all-string-addresses)
      [])
    (catch Exception _
      [])))

(defn address-ranges-to-json
  "Convert address range information to JSON"
  []
  {:bool {:X [1 2000]
          :Y [1 2000]
          :C [1 2000]
          :SC [1 1000]
          :T [1 500]
          :CT [1 250]}
   :word {:XD [1 125]
          :YD [1 125]
          :XS [1 125]
          :YS [1 125]
          :DS [1 10000]
          :DD [1 2000]
          :DH [1 2000]
          :SD [1 1000]
          :TD [1 500]
          :CTD [1 250]}
   :float {:DF [1 2000]}
   :string {:TXT [1 10000]}})

;;; ============================================================
;;; Error Response Helpers
;;; ============================================================

(defn error-response
  "Create a standardized error response"
  [message & {:keys [code details]}]
  {:error true
   :message message
   :code (or code "unknown")
   :details details})

(defn success-response
  "Create a standardized success response"
  [data & {:keys [message]}]
  {:error false
   :data data
   :message message})

;;; ============================================================
;;; Instruction Set JSON
;;; ============================================================

(defn instruction-to-json
  "Convert an instruction definition to JSON"
  [instr]
  (try
    {:opcode (:opcode instr)
     :description (:description instr)
     :type (:type instr)
     :class (:class instr)
     :min-params (:min-params instr)
     :max-params (:max-params instr)}
    (catch Exception _
      {:error "Could not convert instruction"})))

(defn instructions-to-json
  "Convert all instruction definitions to JSON"
  []
  (try
    (let [instructions (instr/list-instructions)]
      (into {}
            (map (fn [instr]
                   [(:opcode instr) (instruction-to-json instr)])
                 instructions)))
    (catch Exception _
      {})))

;;; ============================================================
;;; Batch Operations
;;; ============================================================

(defn get-addresses-with-values
  "Get values for a list of addresses"
  [data-table addresses]
  (try
    (into {}
          (map (fn [addr]
                 [addr (dt/get-value data-table addr)])
               addresses))
    (catch Exception _
      {})))

(defn set-addresses-with-values
  "Set values for multiple addresses"
  [data-table updates]
  (try
    (doseq [[addr value] updates]
      (dt/set-value data-table addr value))
    {:success true :updated (count updates)}
    (catch Exception e
      {:success false :error (str e)})))

;;; ============================================================
;;; Ladder Diagram JSON Conversion
;;; ============================================================

(defn plist-to-json
  "Convert a plist (property list map) to JSON string.
   Uses cheshire for JSON encoding."
  [plist]
  (try
    (json/generate-string plist)
    (catch Exception e
      (json/generate-string {:error (str "JSON conversion failed: " e)}))))

(defn ladder-cell-to-json
  "Convert a ladder cell to JSON-compatible map"
  [cell]
  {:type (str (:type cell))
   :symbol (:symbol cell)
   :address (:address cell)
   :addresses (:addresses cell)
   :opcode (:opcode cell)
   :params (:params cell)
   :row (:row cell)
   :col (:col cell)
   :monitor-type (when (:monitor-type cell) (str (:monitor-type cell)))})

(defn ladder-rung-to-json
  "Convert a ladder rung to JSON-compatible map"
  [rung]
  {:number (:number rung)
   :rows (:rows rung)
   :cols (:cols rung)
   :comment (:comment rung)
   :addresses (:addresses rung)
   :cells (mapv ladder-cell-to-json (:cells rung))
   :il-fallback (:il-fallback rung)})

(defn ladder-program-to-json
  "Convert a ladder program to JSON string"
  [ladder-prog]
  (try
    (json/generate-string
      {:subrname (:name ladder-prog)
       :addresses (sort (:addresses ladder-prog))
       :total-rungs (count (:rungs ladder-prog))
       :subrdata (mapv ladder-rung-to-json (:rungs ladder-prog))})
    (catch Exception e
      (json/generate-string {:error (str "Failed to convert ladder program: " e)}))))

(defn program-to-ladder-json
  "Convert a parsed program to ladder JSON.
   Returns JSON string with ladder data for all subroutines."
  [parsed-program]
  (try
    (let [main-ladder (ladder/program-to-ladder parsed-program :name "main")
          subroutine-names (ladder/list-subroutine-names parsed-program)
          subroutine-ladders (into {}
                               (map (fn [name]
                                      (when (not= name "main")
                                        [name (ladder/program-to-ladder parsed-program :name name)]))
                                    subroutine-names))
          subroutine-ladders (filter (fn [[_k v]] (some? v)) subroutine-ladders)]
      (json/generate-string
        {:main (ladder-program-to-json main-ladder)
         :subroutines (into {}
                        (map (fn [[name ladder-prog]]
                               [name (ladder-program-to-json ladder-prog)])
                             subroutine-ladders))
         :subroutine-list subroutine-names}))
    (catch Exception e
      (json/generate-string {:error (str "Failed to convert program to ladder: " e)}))))

(defn get-ladder-for-subroutine
  "Get ladder JSON for a specific subroutine"
  [parsed-program subroutine-name]
  (try
    (if (= subroutine-name "main")
      (let [ladder-prog (ladder/program-to-ladder parsed-program :name "main")]
        (json/generate-string
          {:status "ok"
           :subrname subroutine-name
           :data (json/parse-string (ladder-program-to-json ladder-prog))}))
      (let [ladder-prog (ladder/program-to-ladder parsed-program :name subroutine-name)]
        (if ladder-prog
          (json/generate-string
            {:status "ok"
             :subrname subroutine-name
             :data (json/parse-string (ladder-program-to-json ladder-prog))})
          (json/generate-string
            {:status "error"
             :message (str "Subroutine not found: " subroutine-name)}))))
    (catch Exception e
      (json/generate-string
        {:status "error"
         :message (str "Failed to get ladder for subroutine: " e)}))))

(defn get-subroutine-list
  "Get list of all subroutine names in a parsed program"
  [parsed-program]
  (try
    (let [names (ladder/list-subroutine-names parsed-program)]
      (json/generate-string
        {:status "ok"
         :subroutines names
         :count (count names)}))
    (catch Exception e
      (json/generate-string
        {:status "error"
         :message (str "Failed to get subroutine list: " e)}))))

;;; End of json-api.clj
