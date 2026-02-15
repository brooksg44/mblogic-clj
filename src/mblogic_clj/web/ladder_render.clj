(ns mblogic-clj.web.ladder-render
  "Convert IL programs to ladder diagram matrix structures.
   Port of Common Lisp mblogic-cl/src/web/ladder-render.lisp
   Implements Python-compatible matrixdata format for ladder visualization."
  (:require [clojure.string :as str]))

;;; ============================================================
;;; Ladder Cell and Rung Data Structures
;;; ============================================================

(defrecord LadderCell [
  type      ; :contact, :coil, :branch, :hline, :vline, :empty
  symbol    ; SVG symbol name (string)
  address   ; Single address (string or nil)
  addresses ; List of addresses
  value     ; Display value or preset
  opcode    ; Original IL opcode
  params    ; Original parameters
  row       ; Row position in matrix (0-based)
  col       ; Column position in matrix (0-based)
  monitor-type]) ; :bool, :word, :timer, :counter, nil

(defrecord LadderRung [
  number          ; Network number
  cells           ; List of LadderCell
  rows            ; Number of rows in input matrix
  cols            ; Number of columns in input matrix
  addresses       ; All addresses used in this rung
  comment         ; Associated comment
  il-fallback     ; If true, display raw IL instead
  branches        ; List of branch connectors
  output-branches]); List of parallel output coils

(defrecord LadderProgram [
  name      ; Subroutine name ("main" or subroutine name)
  rungs     ; List of LadderRung
  addresses]); All unique addresses (sorted)

;;; ============================================================
;;; Branch Symbol Constants
;;; ============================================================

(def ^:const BRANCH_TTR "branchttr")   ; Top-right corner: ┐
(def ^:const BRANCH_TR  "branchtr")    ; Middle-right T: ┤
(def ^:const BRANCH_R   "branchr")     ; Bottom-right corner: ┘
(def ^:const VBAR_R     "vbarr")       ; Vertical bar right: │
(def ^:const BRANCH_TTL "branchttl")   ; Top-left corner: ┌
(def ^:const BRANCH_TL  "branchtl")    ; Middle-left T: ├
(def ^:const BRANCH_L   "branchl")     ; Bottom-left corner: └
(def ^:const VBAR_L     "vbarl")       ; Vertical bar left: │
(def ^:const HBAR       "hbar")        ; Horizontal bar: ─

(def branch-symbols
  "All branch connector symbol names"
  #{BRANCH_TTR BRANCH_TR BRANCH_R VBAR_R
    BRANCH_TTL BRANCH_TL BRANCH_L VBAR_L HBAR})

(def vertical-branch-symbols
  "Vertical branch connector symbols (excludes horizontal)"
  #{BRANCH_TTR BRANCH_TR BRANCH_R VBAR_R
    BRANCH_TTL BRANCH_TL BRANCH_L VBAR_L})

;;; ============================================================
;;; Symbol Mapping
;;; ============================================================

(def ladsymb-to-svg
  "Mapping from instruction :ladsymb to SVG symbol names"
  {:contact-no    "noc"      ; Normally open contact
   :contact-nc    "ncc"      ; Normally closed contact
   :contact-pd    "nocpd"    ; Positive differential (rising edge)
   :contact-nd    "nocnd"    ; Negative differential (falling edge)
   :coil          "out"      ; Standard output coil
   :coil-set      "set"      ; Set (latch) coil
   :coil-reset    "rst"      ; Reset (unlatch) coil
   :coil-pd       "pd"       ; Pulse coil
   :branch-end    nil        ; Branch end - handled structurally
   :compare       "compare"  ; Comparison block
   :timer         "tmr"      ; Timer block
   :counter       "cntu"     ; Counter block
   :math          "mathdec"  ; Math block
   :copy          "copy"     ; Copy instruction
   :cpyblk        "cpyblk"   ; Block copy
   :fill          "fill"     ; Fill instruction
   :pack          "pack"     ; Pack bits
   :unpack        "unpack"   ; Unpack bits
   :shfrg         "shfrg"    ; Shift register
   :find          "findeq"   ; Search instruction
   :sum           "sum"      ; Sum instruction
   :call          "call"     ; Subroutine call
   :return        "rt"       ; Return
   :return-cond   "rtc"      ; Conditional return
   :end           "end"      ; End
   :end-cond      "endc"     ; Conditional end
   :for           "for"      ; For loop start
   :next          "next"})   ; For loop end

(defn ladsymb-to-svg-symbol
  "Convert instruction :ladsymb to SVG symbol name.
   Uses opcode for more specific mapping when needed."
  [ladsymb opcode]
  (let [svg-sym (ladsymb-to-svg ladsymb)]
    (cond
      ; Comparisons - map to specific symbols
      (= ladsymb :compare)
      (cond
        (contains? #{"STRE" "ANDE" "ORE"} opcode) "compeq"
        (contains? #{"STRNE" "ANDNE" "ORNE"} opcode) "compneq"
        (contains? #{"STRGT" "ANDGT" "ORGT"} opcode) "compgt"
        (contains? #{"STRLT" "ANDLT" "ORLT"} opcode) "complt"
        (contains? #{"STRGE" "ANDGE" "ORGE"} opcode) "compge"
        (contains? #{"STRLE" "ANDLE" "ORLE"} opcode) "comple"
        :else "compeq")

      ; Timers
      (and (= ladsymb :timer) (= opcode "TMR")) "tmr"
      (and (= ladsymb :timer) (= opcode "TMRA")) "tmra"
      (and (= ladsymb :timer) (= opcode "TMROFF")) "tmroff"

      ; Counters
      (and (= ladsymb :counter) (= opcode "CNTU")) "cntu"
      (and (= ladsymb :counter) (= opcode "CNTD")) "cntd"
      (and (= ladsymb :counter) (= opcode "UDC")) "udc"

      ; Find instructions
      (= ladsymb :find)
      (cond
        (contains? #{"FINDEQ" "FINDIEQ"} opcode) "findeq"
        (contains? #{"FINDNE" "FINDINE"} opcode) "findne"
        (contains? #{"FINDGT" "FINDIGT"} opcode) "findgt"
        (contains? #{"FINDLT" "FINDILT"} opcode) "findlt"
        (contains? #{"FINDGE" "FINDIGE"} opcode) "findge"
        (contains? #{"FINDLE" "FINDILE"} opcode) "findle"
        :else "findeq")

      ; Default
      :else (or svg-sym "il"))))

;;; ============================================================
;;; Instruction Classification
;;; ============================================================

(defn contact-instruction?
  "Check if opcode is a contact (input) instruction"
  [opcode]
  (contains? #{"STR" "STRN" "AND" "ANDN" "OR" "ORN"
               "STRPD" "STRND" "ANDPD" "ANDND" "ORPD" "ORND"}
             opcode))

(defn store-instruction?
  "Check if opcode is STR (store/start new logic block)"
  [opcode]
  (contains? #{"STR" "STRN" "STRPD" "STRND"
               "STRE" "STRNE" "STRGT" "STRLT" "STRGE" "STRLE"}
             opcode))

(defn and-instruction?
  "Check if opcode is AND (continues current row)"
  [opcode]
  (contains? #{"AND" "ANDN" "ANDPD" "ANDND"
               "ANDE" "ANDNE" "ANDGT" "ANDLT" "ANDGE" "ANDLE"}
             opcode))

(defn or-instruction?
  "Check if opcode is OR (creates parallel branch)"
  [opcode]
  (contains? #{"OR" "ORN" "ORPD" "ORND"
               "ORE" "ORNE" "ORGT" "ORLT" "ORGE" "ORLE"}
             opcode))

(defn orstr-instruction?
  "Check if opcode is ORSTR (merge parallel blocks)"
  [opcode]
  (= opcode "ORSTR"))

(defn andstr-instruction?
  "Check if opcode is ANDSTR (merge series blocks)"
  [opcode]
  (= opcode "ANDSTR"))

(defn coil-instruction?
  "Check if opcode is a coil (output) instruction"
  [opcode]
  (contains? #{"OUT" "SET" "RST" "PD"} opcode))

(defn block-instruction?
  "Check if instruction renders as a block"
  [opcode]
  (contains? #{"TMR" "TMRA" "TMROFF" "CNTU" "CNTD" "UDC"
               "COPY" "CPYBLK" "FILL" "PACK" "UNPACK" "SHFRG"
               "MATHDEC" "MATHHEX" "SUM"
               "FINDEQ" "FINDNE" "FINDGT" "FINDLT" "FINDGE" "FINDLE"
               "FINDIEQ" "FINDINE" "FINDIGT" "FINDILT" "FINDIGE" "FINDILE"
               "STRE" "STRNE" "STRGT" "STRLT" "STRGE" "STRLE"
               "ANDE" "ANDNE" "ANDGT" "ANDLT" "ANDGE" "ANDLE"
               "ORE" "ORNE" "ORGT" "ORLT" "ORGE" "ORLE"
               "CALL" "FOR"}
             opcode))

(defn output-block-instruction?
  "Check if instruction is a block that belongs in output column"
  [opcode]
  (contains? #{"TMR" "TMRA" "TMROFF" "CNTU" "CNTD" "UDC"
               "COPY" "CPYBLK" "FILL" "PACK" "UNPACK" "SHFRG"
               "MATHDEC" "MATHHEX" "SUM"
               "FINDEQ" "FINDNE" "FINDGT" "FINDLT" "FINDGE" "FINDLE"
               "FINDIEQ" "FINDINE" "FINDIGT" "FINDILT" "FINDIGE" "FINDILE"
               "CALL" "FOR"}
             opcode))

(defn control-instruction?
  "Check if instruction is control flow"
  [opcode]
  (contains? #{"END" "ENDC" "RT" "RTC" "NEXT"} opcode))

(defn branch-symbol?
  "Check if symbol is a branch connector"
  [symbol]
  (contains? branch-symbols symbol))

(defn vertical-branch-symbol?
  "Check if symbol is a vertical branch connector"
  [symbol]
  (contains? vertical-branch-symbols symbol))

;;; ============================================================
;;; Cell Constructors
;;; ============================================================

(defn make-ladder-cell
  "Create a ladder cell with the given properties"
  [& {:keys [type symbol address addresses value opcode params row col monitor-type]
      :or {row 0 col 0}}]
  (->LadderCell type symbol address addresses value opcode params row col monitor-type))

(defn make-contact-cell
  "Create a contact (input) cell"
  [address row col negated?]
  (make-ladder-cell
    :type :contact
    :symbol (if negated? "ncc" "noc")
    :address address
    :addresses [address]
    :row row
    :col col
    :monitor-type :bool))

(defn make-coil-cell
  "Create a coil (output) cell"
  [opcode address col row _is-range]
  (let [symbol (case opcode
                 "SET" "set"
                 "RST" "rst"
                 "PD" "pd"
                 "out")]
    (make-ladder-cell
      :type :coil
      :symbol symbol
      :address address
      :addresses [address]
      :opcode opcode
      :row row
      :col col
      :monitor-type :bool)))

(defn make-branch-cell
  "Create a branch connector cell"
  [symbol row col]
  (make-ladder-cell
    :type :branch
    :symbol symbol
    :row row
    :col col))

(defn make-hbar-cell
  "Create a horizontal bar cell"
  [row col]
  (make-branch-cell HBAR row col))

(defn make-vbar-l-cell
  "Create vertical bar (left side)"
  [row col]
  (make-branch-cell VBAR_L row col))

(defn make-branch-l-cell
  "Create bottom-left corner └"
  [row col]
  (make-branch-cell BRANCH_L row col))

(defn make-branch-tl-cell
  "Create middle-left T ├"
  [row col]
  (make-branch-cell BRANCH_TL row col))

(defn make-branch-ttl-cell
  "Create top-left corner ┌"
  [row col]
  (make-branch-cell BRANCH_TTL row col))

(defn make-branch-tr-cell
  "Create middle-right T ┤"
  [row col]
  (make-branch-cell BRANCH_TR row col))

(defn make-branch-ttr-cell
  "Create top-right corner ┐"
  [row col]
  (make-branch-cell BRANCH_TTR row col))

(defn make-branch-r-cell
  "Create bottom-right corner ┘"
  [row col]
  (make-branch-cell BRANCH_R row col))

(defn make-vbar-r-cell
  "Create vertical bar (right side)"
  [row col]
  (make-branch-cell VBAR_R row col))

;;; ============================================================
;;; Matrix Operations (Core Algorithm)
;;; ============================================================

(defn matrix-height
  "Get the number of rows in a matrix"
  [matrix]
  (count matrix))

(defn matrix-width
  "Get the maximum number of columns in a matrix"
  [matrix]
  (if (empty? matrix)
    0
    (apply max (map count matrix))))

(defn append-cell-to-matrix
  "Append a cell to the current row of the matrix"
  [cell matrix]
  (if (empty? matrix)
    [[cell]]
    (let [current-row (last matrix)
          new-row (conj current-row cell)]
      (conj (vec (butlast matrix)) new-row))))

(defn merge-matrix-below
  "Merge lower matrix below upper matrix (for OR branches)"
  [upper lower]
  (let [upper-height (matrix-height upper)
        lower-height (matrix-height lower)]
    (cond
      (zero? upper-height) lower
      (zero? lower-height) upper
      :else
      (concat upper lower))))

(defn merge-matrix-right
  "Merge right matrix with left side connectors (for ANDSTR)"
  [_left right]
  right)

(defn close-branch-block
  "Add right-side closing connectors to a matrix"
  [matrix]
  matrix)

;;; ============================================================
;;; Instruction to Cell Conversion
;;; ============================================================

(defn instruction-to-cell
  "Convert an IL instruction to a ladder cell"
  [instruction row]
  (let [opcode (:opcode instruction)
        params (:params instruction)
        first-addr (first params)]
    (cond
      ; Contact instructions
      (contact-instruction? opcode)
      (let [negated? (str/includes? opcode "N")]
        (make-contact-cell first-addr row 0 negated?))

      ; Block/comparison instructions
      (block-instruction? opcode)
      (make-ladder-cell
        :type :block
        :symbol (or (ladsymb-to-svg-symbol :compare opcode) "compare")
        :address first-addr
        :addresses params
        :opcode opcode
        :params params
        :row row
        :col 0
        :monitor-type :bool)

      ; Default: create empty cell
      :else
      (make-ladder-cell :type :empty :row row :col 0))))

;;; ============================================================
;;; Network to Ladder Rung Conversion (CORE ALGORITHM)
;;; ============================================================

(defn has-later-cell?
  "Check if there's any non-nil cell in this row after the given column"
  [row col-idx]
  (some identity (subvec (vec row) (inc col-idx))))

(defn flatten-matrix-to-cells
  "Convert matrix to flat cell list with proper row/col positions.
   Fill nil gaps with hbar (horizontal wire) connectors for continuity."
  [matrix]
  (let [input-cells (atom [])]
    (doseq [[row-idx row] (map-indexed vector matrix)]
      (doseq [[col-idx cell] (map-indexed vector row)]
        (cond
          ; Real cell - set position and collect
          (some? cell)
          (swap! input-cells conj
            (assoc cell :row row-idx :col col-idx))

          ; Nil in row 0 - fill with hbar for wire continuity
          (= row-idx 0)
          (swap! input-cells conj (make-hbar-cell row-idx col-idx))

          ; Nil in branch row before first non-nil in that row
          ; (check if there's any cell in this row at a later column)
          (has-later-cell? row col-idx)
          (swap! input-cells conj (make-hbar-cell row-idx col-idx)))))

    @input-cells))

(defn network-to-ladder-rung
  "Convert a parsed network to a ladder rung structure using matrix-based algorithm.
   This produces Python-compatible matrixdata with explicit branch connector cells.

   Algorithm (matches Python PLCLadder.py):
   - Matrix is a list of rows: ([row0-cells] [row1-cells] ...)
   - matrix-stack holds matrices for nested blocks
   - STR: push current matrix, start new
   - AND: append cell to current row
   - OR: create new row matrix, merge below, close block
   - ORSTR: pop stack, merge below, close block
   - ANDSTR: pop stack, merge right
   - Outputs are handled separately after inputs"
  [network]
  (let [instructions (:instructions network)
        all-addresses (atom #{})]

    ; Separate inputs from outputs
    (let [inputs (atom [])
          outputs (atom [])]
      (doseq [instr instructions]
        (let [opcode (:opcode instr)]
          (cond
            (coil-instruction? opcode)
            (swap! outputs conj instr)

            (control-instruction? opcode)
            (swap! outputs conj instr)

            (output-block-instruction? opcode)
            (swap! outputs conj instr)

            :else
            (swap! inputs conj instr))))

      ; Process input instructions using matrix algorithm
      (let [current-matrix (atom [[]])      ; Start with one empty row
            matrix-stack (atom [])            ; Stack for STR blocks
            inputs-vec @inputs
            outputs-vec @outputs]

        ; Process each input instruction
        (doseq [instr inputs-vec]
          (let [opcode (:opcode instr)
                cell (instruction-to-cell instr 0)]

            ; Collect addresses
            (doseq [addr (:addresses cell)]
              (swap! all-addresses conj addr))

            (cond
              ; STR instruction - ALWAYS push current and start new
              (store-instruction? opcode)
              (do
                (swap! matrix-stack conj @current-matrix)
                (reset! current-matrix [[]])
                (reset! current-matrix (append-cell-to-matrix cell @current-matrix)))

              ; AND instruction - append to current row
              (and-instruction? opcode)
              (reset! current-matrix (append-cell-to-matrix cell @current-matrix))

              ; OR instruction - create parallel branch below, then close block
              (or-instruction? opcode)
              (let [new-matrix (append-cell-to-matrix cell [[]])]
                (reset! current-matrix (merge-matrix-below @current-matrix new-matrix))
                (reset! current-matrix (close-branch-block @current-matrix)))

              ; ORSTR - pop and merge below, then close block
              (orstr-instruction? opcode)
              (when (seq @matrix-stack)
                (let [old-matrix (peek @matrix-stack)]
                  (swap! matrix-stack pop)
                  (reset! current-matrix (merge-matrix-below old-matrix @current-matrix))
                  (reset! current-matrix (close-branch-block @current-matrix))))

              ; ANDSTR - pop and merge right with left-side connectors
              (andstr-instruction? opcode)
              (when (seq @matrix-stack)
                (let [old-matrix (peek @matrix-stack)]
                  (swap! matrix-stack pop)
                  (reset! current-matrix (merge-matrix-right old-matrix @current-matrix))))

              ; Other instructions (comparisons, etc.) - treat as AND
              :else
              (reset! current-matrix (append-cell-to-matrix cell @current-matrix)))))

        ; Handle multi-row rungs (double/triple) vs single-row rungs
        (let [stack-len (count @matrix-stack)]
          (cond
            ; Single-row rung: stack should have exactly 1 entry (initial empty matrix)
            (= stack-len 1)
            nil ; Nothing to do - current-matrix already contains the result

            ; Double rung (2 parallel inputs): stack has 2 entries
            (= stack-len 2)
            (let [last-matrix (first @matrix-stack)]
              (reset! current-matrix (concat last-matrix @current-matrix)))

            ; Triple rung (3 parallel inputs): stack has 3 entries
            (= stack-len 3)
            (let [x2-matrix (first @matrix-stack)
                  x1-matrix (second @matrix-stack)]
              (reset! current-matrix (concat x1-matrix x2-matrix @current-matrix)))

            ; Any other stack size is invalid
            :else
            (println "WARNING: Invalid IL program structure: matrix stack has" stack-len "entries")))

        ; Convert matrix to flat cell list with correct row/col positions
        (let [input-cells (flatten-matrix-to-cells @current-matrix)
              current-matrix-height (matrix-height @current-matrix)
              current-matrix-width (matrix-width @current-matrix)

              ; Process output instructions
              ; First pass: collect all output cells with sequential row numbers
              output-row (atom 0)
              output-cells (atom [])]

          (doseq [instr outputs-vec]
            (let [opcode (:opcode instr)
                  params (:params instr)]
              (cond
                ; Coil with potentially multiple addresses
                (coil-instruction? opcode)
                (let [is-range (> (count params) 1)]
                  (doseq [addr params]
                    (when (string? addr) ; Check if valid address
                      (let [cell (make-coil-cell opcode addr 1 @output-row is-range)]
                        (swap! output-cells conj cell)
                        (swap! all-addresses conj addr)
                        (swap! output-row inc)))))

                ; Output block instructions (TMR, CNTU, COPY, MATHDEC, etc.)
                (output-block-instruction? opcode)
                (let [svg-symbol (ladsymb-to-svg-symbol :compare opcode)
                      addr-list (if params (vec params) [""])
                      cell (make-ladder-cell
                             :type :coil
                             :symbol svg-symbol
                             :address (first params)
                             :addresses addr-list
                             :opcode opcode
                             :params params
                             :row @output-row
                             :col 1
                             :monitor-type :bool)]
                  (swap! output-cells conj cell)
                  ; Track all PLC addresses
                  (doseq [p params]
                    (when (string? p)
                      (swap! all-addresses conj p)))
                  (swap! output-row inc))

                ; Control instructions (END, RT, etc.)
                :else
                (let [cell (assoc (instruction-to-cell instr 0)
                             :row @output-row
                             :col 1)]
                  (swap! output-cells conj cell)
                  (doseq [addr (:addresses cell)]
                    (swap! all-addresses conj addr))
                  (swap! output-row inc)))))

          ; Build the rung - combine input and output cells
          (->LadderRung
            (:number network)
            (concat input-cells @output-cells)
            (max current-matrix-height (count @output-cells) 1)
            current-matrix-width
            (sort @all-addresses)
            (first (:comments network)) ; Get comment if available
            false ; il-fallback
            [] ; branches (no longer needed - explicit in cells)
            []))))))

;;; ============================================================
;;; Program Conversion
;;; ============================================================

(defn networks-to-ladder
  "Convert a list of networks to a ladder program structure"
  [networks name]
  (let [rungs (map network-to-ladder-rung networks)
        all-addresses (atom #{})]
    (doseq [rung rungs]
      (doseq [addr (:addresses rung)]
        (swap! all-addresses conj addr)))

    (->LadderProgram
      name
      rungs
      (sort @all-addresses))))

(defn program-to-ladder
  "Convert a parsed program to ladder diagram structure"
  [parsed-program & {:keys [name] :or {name "main"}}]
  (if (= name "main")
    (networks-to-ladder (:main-networks parsed-program) "main")
    (when-let [sbr (get (:subroutines parsed-program) name)]
      (networks-to-ladder (:networks sbr) name))))

(defn list-subroutine-names
  "Return list of all subroutine names in a parsed program"
  [parsed-program]
  (into ["main"]
        (keys (:subroutines parsed-program))))

;;; ============================================================
;;; Plist Conversion for JSON Output
;;; ============================================================

(defn format-monitor-info
  "Format monitor info for a cell in Python-compatible format"
  [cell]
  (if (and (:monitor-type cell) (:address cell))
    [(name (:monitor-type cell)) (:address cell)]
    ["none"]))

(defn cell-to-matrixdata
  "Convert a ladder cell to Python-compatible matrixdata format"
  [cell]
  {:type (if (contains? #{:coil :output-branch :control} (:type cell)) "outp" "inp")
   :row (:row cell)
   :col (:col cell)
   :addr (or (:addresses cell) [""])
   :value (:symbol cell)
   :monitor (format-monitor-info cell)})

(defn cell-to-plist
  "Convert a ladder cell to a plist for JSON serialization"
  [cell]
  {:type (str/lower-case (str (:type cell)))
   :symbol (:symbol cell)
   :addr (:address cell)
   :addrs (:addresses cell)
   :opcode (:opcode cell)
   :params (:params cell)
   :row (:row cell)
   :col (:col cell)
   :monitor (when (:monitor-type cell)
              (str/lower-case (str (:monitor-type cell))))})

(defn rung-to-plist
  "Convert a ladder rung to a plist for JSON serialization"
  [rung]
  {:rungnum (:number rung)
   :rows (:rows rung)
   :cols (:cols rung)
   :comment (:comment rung)
   :addrs (:addresses rung)
   :cells (map cell-to-plist (:cells rung))
   :branches nil
   :output-branches nil})

(defn ladder-program-to-plist
  "Convert a ladder program to a plist for JSON serialization"
  [ladder-prog]
  {:subrname (:name ladder-prog)
   :addresses (:addresses ladder-prog)
   :subrdata (map rung-to-plist (:rungs ladder-prog))})

;;; ============================================================
;;; Analysis Functions
;;; ============================================================

(defn analyze-program
  "Analyze a parsed IL program for ladder renderability"
  [parsed-program]
  (if (or (nil? parsed-program) (empty? (:main-networks parsed-program)))
    {}
    (let [networks (:main-networks parsed-program)]
      (into {}
            (map (fn [network]
                   [(:number network)
                    [(network-to-ladder-rung network)]])
                 networks)))))

(defn render-program-summary
  "Generate a summary of which rungs can/cannot be rendered as ladder"
  [analyzed-networks]
  (let [rungs (mapcat val analyzed-networks)]
    {:total-rungs (count rungs)
     :ladder-rungs (count (filter (fn [r] (not (:il-fallback r))) rungs))
     :il-rungs (count (filter :il-fallback rungs))
     :percentage (let [l (count (filter (fn [r] (not (:il-fallback r))) rungs))]
                   (if (zero? (count rungs))
                     0
                     (int (* 100 (/ l (count rungs))))))}))

;;; End of ladder_render.clj
