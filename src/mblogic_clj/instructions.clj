(ns mblogic-clj.instructions
  "PLC Instruction definitions.
   Defines the complete IL instruction set with 73 instructions.
   Ported from: src/instructions.lisp")

;;; ============================================================
;;; Instruction Type and Class Definitions
;;; ============================================================

(def instruction-types
  "Valid instruction data type categories"
  #{:boolean :word :float :string :mixed :none})

(def instruction-classes
  "Valid instruction functional classifications"
  #{:boolean-input      ; STR, AND, OR variants
    :boolean-output     ; OUT, SET, RST, PD
    :boolean-compare    ; STRE, STRGT, etc.
    :edge-contact       ; STRPD, ANDPD, etc.
    :stack-operation    ; ANDSTR, ORSTR
    :timer              ; TMR, TMRA, TMROFF
    :counter            ; CNTU, CNTD, UDC
    :data-move          ; COPY, CPYBLK, FILL
    :data-pack          ; PACK, UNPACK
    :math               ; MATHDEC, MATHHEX
    :search             ; FIND* variants
    :control            ; CALL, RT, END, FOR, NEXT
    :special            ; NETWORK, SBR, SHFRG
    :no-op})            ; Comments, blank lines

;;; ============================================================
;;; Instruction Record Definition
;;; ============================================================

(defrecord PlcInstruction
  [opcode       ; Instruction mnemonic (e.g., STR, AND, OUT)
   description  ; Human-readable description
   type         ; Data type this instruction operates on
   class        ; Functional classification
   min-params   ; Minimum number of parameters
   max-params   ; Maximum number of parameters
   param-types  ; List of parameter type specifications
   validator    ; Optional validation function
   ladsymb      ; Ladder diagram symbol type
   monitor])    ; Monitoring category

(defmethod print-method PlcInstruction [instr writer]
  (.write writer (str "#<PlcInstruction " (:opcode instr) ">")))

;;; ============================================================
;;; Parameter Type Regex Validators
;;; ============================================================

(def bool-addr-pattern #"^(X|Y|C|SC|T|CT)[0-9]+$")
(def word-addr-pattern #"^(DS|DD|DH|XD|YD|XS|YS|SD|TD|CTD)[0-9]+$")
(def float-addr-pattern #"^DF[0-9]+$")
(def string-addr-pattern #"^TXT[0-9]+$")
(def any-addr-pattern #"^(X|Y|C|SC|T|CT|DS|DD|DH|XD|YD|XS|YS|SD|TD|CTD|DF|TXT)[0-9]+$")
(def numeric-pattern #"^-?[0-9]+\.?[0-9]*$")
(def hex-constant-pattern #"^[0-9A-Fa-f]+[hH]$")

;;; ============================================================
;;; Address Type Validators
;;; ============================================================

(defn bool-addr-p
  "Check if string is a valid boolean address"
  [str]
  (and (string? str) (re-matches bool-addr-pattern str)))

(defn word-addr-p
  "Check if string is a valid word address"
  [str]
  (and (string? str) (re-matches word-addr-pattern str)))

(defn float-addr-p
  "Check if string is a valid float address"
  [str]
  (and (string? str) (re-matches float-addr-pattern str)))

(defn string-addr-p
  "Check if string is a valid string address"
  [str]
  (and (string? str) (re-matches string-addr-pattern str)))

(defn any-addr-p
  "Check if string is any valid address type"
  [str]
  (and (string? str) (re-matches any-addr-pattern str)))

(defn numeric-p
  "Check if string is a numeric constant"
  [str]
  (and (string? str) (re-matches numeric-pattern str)))

(defn hex-constant-p
  "Check if string is a hex constant (ends with h)"
  [str]
  (and (string? str) (re-matches hex-constant-pattern str)))

(defn time-unit-p
  "Check if string is a valid time unit"
  [str]
  (and (string? str)
       (contains? #{"ms" "sec" "min" "hour" "day"}
                  (.toLowerCase str))))

;;; ============================================================
;;; Instruction Registry
;;; ============================================================

(def ^:private *instruction-set*
  "Hash map mapping opcode strings to instruction objects"
  (atom {}))

(defn register-instruction
  "Register an instruction in the instruction set"
  [instr]
  (swap! *instruction-set*
         assoc (:opcode instr) instr)
  instr)

(defn find-instruction
  "Find instruction by opcode string (case-insensitive)"
  [opcode]
  (get @*instruction-set* (.toUpperCase (str opcode))))

(defn list-instructions
  "Return vector of all registered instructions"
  []
  (vec (vals @*instruction-set*)))

(defn list-instructions-by-class
  "Return vector of instructions of a specific class"
  [cls]
  (vec (filter (fn [instr] (= (:class instr) cls))
               (vals @*instruction-set*))))

(defn instruction-count
  "Return count of registered instructions"
  []
  (count @*instruction-set*))

;;; ============================================================
;;; Instruction Definition Helper
;;; ============================================================

(defn define-instruction
  "Define and register a PLC instruction"
  [opcode & {:keys [description type class min-params max-params
                    param-types validator ladsymb monitor]
             :or {min-params 0, max-params 0, param-types [], validator nil,
                  ladsymb nil, monitor nil}}]
  (register-instruction
    (PlcInstruction. (.toUpperCase (str opcode))
                     description
                     type
                     class
                     min-params
                     max-params
                     param-types
                     validator
                     ladsymb
                     monitor)))

;;; ============================================================
;;; Boolean Input Instructions
;;; ============================================================

(define-instruction "STR"
  :description "Store - Load boolean address onto logic stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-no
  :monitor :bool)

(define-instruction "STRN"
  :description "Store Not - Load inverted boolean onto logic stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nc
  :monitor :bool)

(define-instruction "AND"
  :description "AND - AND boolean with top of stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-no
  :monitor :bool)

(define-instruction "ANDN"
  :description "AND Not - AND inverted boolean with top of stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nc
  :monitor :bool)

(define-instruction "OR"
  :description "OR - OR boolean with top of stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-no
  :monitor :bool)

(define-instruction "ORN"
  :description "OR Not - OR inverted boolean with top of stack"
  :type :boolean
  :class :boolean-input
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nc
  :monitor :bool)

;;; ============================================================
;;; Stack Operations
;;; ============================================================

(define-instruction "ANDSTR"
  :description "AND Stack - AND top two stack elements"
  :type :boolean
  :class :stack-operation
  :min-params 0
  :max-params 0
  :ladsymb :branch-end)

(define-instruction "ORSTR"
  :description "OR Stack - OR top two stack elements"
  :type :boolean
  :class :stack-operation
  :min-params 0
  :max-params 0
  :ladsymb :branch-end)

;;; ============================================================
;;; Boolean Output Instructions
;;; ============================================================

(define-instruction "OUT"
  :description "Output - Write top of stack to boolean address(es)"
  :type :boolean
  :class :boolean-output
  :min-params 1
  :max-params 8
  :param-types [:bool-addr]
  :ladsymb :coil
  :monitor :bool)

(define-instruction "SET"
  :description "Set - Latch boolean address(es) when stack is true"
  :type :boolean
  :class :boolean-output
  :min-params 1
  :max-params 8
  :param-types [:bool-addr]
  :ladsymb :coil-set
  :monitor :bool)

(define-instruction "RST"
  :description "Reset - Unlatch boolean address(es) when stack is true"
  :type :boolean
  :class :boolean-output
  :min-params 1
  :max-params 8
  :param-types [:bool-addr]
  :ladsymb :coil-reset
  :monitor :bool)

(define-instruction "PD"
  :description "Pulse/Differentiate - One-shot output on rising edge"
  :type :boolean
  :class :boolean-output
  :min-params 1
  :max-params 8
  :param-types [:bool-addr]
  :ladsymb :coil-pd
  :monitor :bool)

;;; ============================================================
;;; Edge Detection Instructions
;;; ============================================================

(define-instruction "STRPD"
  :description "Store Positive Differential - Detect rising edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-pd
  :monitor :bool)

(define-instruction "STRND"
  :description "Store Negative Differential - Detect falling edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nd
  :monitor :bool)

(define-instruction "ANDPD"
  :description "AND Positive Differential - AND with rising edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-pd
  :monitor :bool)

(define-instruction "ANDND"
  :description "AND Negative Differential - AND with falling edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nd
  :monitor :bool)

(define-instruction "ORPD"
  :description "OR Positive Differential - OR with rising edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-pd
  :monitor :bool)

(define-instruction "ORND"
  :description "OR Negative Differential - OR with falling edge"
  :type :boolean
  :class :edge-contact
  :min-params 1
  :max-params 1
  :param-types [:bool-addr]
  :ladsymb :contact-nd
  :monitor :bool)

;;; ============================================================
;;; Comparison Instructions - Store Variants
;;; ============================================================

(define-instruction "STRE"
  :description "Store Equal - Compare two values for equality"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "STRNE"
  :description "Store Not Equal - Compare two values for inequality"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "STRGT"
  :description "Store Greater Than - Compare if first > second"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "STRLT"
  :description "Store Less Than - Compare if first < second"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "STRGE"
  :description "Store Greater/Equal - Compare if first >= second"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "STRLE"
  :description "Store Less/Equal - Compare if first <= second"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

;;; ============================================================
;;; Comparison Instructions - AND Variants
;;; ============================================================

(define-instruction "ANDE"
  :description "AND Equal - AND stack with equality comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ANDNE"
  :description "AND Not Equal - AND stack with inequality comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ANDGT"
  :description "AND Greater Than - AND stack with > comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ANDLT"
  :description "AND Less Than - AND stack with < comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ANDGE"
  :description "AND Greater/Equal - AND stack with >= comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ANDLE"
  :description "AND Less/Equal - AND stack with <= comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

;;; ============================================================
;;; Comparison Instructions - OR Variants
;;; ============================================================

(define-instruction "ORE"
  :description "OR Equal - OR stack with equality comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ORNE"
  :description "OR Not Equal - OR stack with inequality comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ORGT"
  :description "OR Greater Than - OR stack with > comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ORLT"
  :description "OR Less Than - OR stack with < comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ORGE"
  :description "OR Greater/Equal - OR stack with >= comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

(define-instruction "ORLE"
  :description "OR Less/Equal - OR stack with <= comparison"
  :type :word
  :class :boolean-compare
  :min-params 2
  :max-params 2
  :param-types [:word-or-const :word-or-const]
  :ladsymb :compare
  :monitor :word)

;;; ============================================================
;;; Timer Instructions
;;; ============================================================

(define-instruction "TMR"
  :description "Timer On-Delay - Timer with preset and time base"
  :type :mixed
  :class :timer
  :min-params 2
  :max-params 3
  :param-types [:timer-addr :word-or-const :time-unit]
  :ladsymb :timer
  :monitor :timer)

(define-instruction "TMRA"
  :description "Timer Accumulating - Retentive on-delay timer"
  :type :mixed
  :class :timer
  :min-params 2
  :max-params 3
  :param-types [:timer-addr :word-or-const :time-unit]
  :ladsymb :timer
  :monitor :timer)

(define-instruction "TMROFF"
  :description "Timer Off-Delay - Off-delay timer"
  :type :mixed
  :class :timer
  :min-params 2
  :max-params 3
  :param-types [:timer-addr :word-or-const :time-unit]
  :ladsymb :timer
  :monitor :timer)

;;; ============================================================
;;; Counter Instructions
;;; ============================================================

(define-instruction "CNTU"
  :description "Counter Up - Increment counter on rising edge"
  :type :mixed
  :class :counter
  :min-params 2
  :max-params 2
  :param-types [:counter-addr :word-or-const]
  :ladsymb :counter
  :monitor :counter)

(define-instruction "CNTD"
  :description "Counter Down - Decrement counter on rising edge"
  :type :mixed
  :class :counter
  :min-params 2
  :max-params 2
  :param-types [:counter-addr :word-or-const]
  :ladsymb :counter
  :monitor :counter)

(define-instruction "UDC"
  :description "Up/Down Counter - Bidirectional counter"
  :type :mixed
  :class :counter
  :min-params 2
  :max-params 2
  :param-types [:counter-addr :word-or-const]
  :ladsymb :counter
  :monitor :counter)

;;; ============================================================
;;; Data Movement Instructions
;;; ============================================================

(define-instruction "COPY"
  :description "Copy - Copy single value from source to destination"
  :type :mixed
  :class :data-move
  :min-params 2
  :max-params 2
  :param-types [:any-or-const :any-addr]
  :ladsymb :copy
  :monitor :word)

(define-instruction "CPYBLK"
  :description "Copy Block - Copy range of values"
  :type :mixed
  :class :data-move
  :min-params 3
  :max-params 3
  :param-types [:any-addr :any-addr :any-addr]
  :ladsymb :cpyblk
  :monitor :word)

(define-instruction "FILL"
  :description "Fill - Fill range with constant value"
  :type :mixed
  :class :data-move
  :min-params 3
  :max-params 3
  :param-types [:any-or-const :any-addr :any-addr]
  :ladsymb :fill
  :monitor :word)

;;; ============================================================
;;; Pack/Unpack Instructions
;;; ============================================================

(define-instruction "PACK"
  :description "Pack - Pack boolean range into word"
  :type :mixed
  :class :data-pack
  :min-params 3
  :max-params 3
  :param-types [:bool-addr :bool-addr :word-addr]
  :ladsymb :pack
  :monitor :word)

(define-instruction "UNPACK"
  :description "Unpack - Unpack word into boolean range"
  :type :mixed
  :class :data-pack
  :min-params 3
  :max-params 3
  :param-types [:word-addr :bool-addr :bool-addr]
  :ladsymb :unpack
  :monitor :word)

;;; ============================================================
;;; Math Instructions
;;; ============================================================

(define-instruction "MATHDEC"
  :description "Math Decimal - Evaluate decimal math expression"
  :type :mixed
  :class :math
  :min-params 3
  :max-params 100
  :param-types [:any-addr :flags :expression]
  :ladsymb :math
  :monitor :word)

(define-instruction "MATHHEX"
  :description "Math Hex - Evaluate hexadecimal/bitwise expression"
  :type :mixed
  :class :math
  :min-params 3
  :max-params 100
  :param-types [:any-addr :flags :expression]
  :ladsymb :math
  :monitor :word)

(define-instruction "SUM"
  :description "Sum - Sum array of values"
  :type :word
  :class :math
  :min-params 4
  :max-params 4
  :param-types [:word-addr :word-addr :word-addr :flags]
  :ladsymb :sum
  :monitor :word)

;;; ============================================================
;;; Search Instructions - Non-incremental
;;; ============================================================

(define-instruction "FINDEQ"
  :description "Find Equal - Search for equal value in table"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDNE"
  :description "Find Not Equal - Search for not equal value"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDGT"
  :description "Find Greater Than - Search for > value"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDLT"
  :description "Find Less Than - Search for < value"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDGE"
  :description "Find Greater/Equal - Search for >= value"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDLE"
  :description "Find Less/Equal - Search for <= value"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

;;; ============================================================
;;; Search Instructions - Incremental
;;; ============================================================

(define-instruction "FINDIEQ"
  :description "Find Incremental Equal - Continue search for equal"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDINE"
  :description "Find Incremental Not Equal - Continue search"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDIGT"
  :description "Find Incremental Greater Than - Continue search"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDILT"
  :description "Find Incremental Less Than - Continue search"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDIGE"
  :description "Find Incremental Greater/Equal - Continue search"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

(define-instruction "FINDILE"
  :description "Find Incremental Less/Equal - Continue search"
  :type :mixed
  :class :search
  :min-params 5
  :max-params 6
  :param-types [:any-or-const :any-addr :any-addr :word-addr :bool-addr :flags]
  :ladsymb :find
  :monitor :word)

;;; ============================================================
;;; Control Flow Instructions
;;; ============================================================

(define-instruction "CALL"
  :description "Call - Call subroutine"
  :type :none
  :class :control
  :min-params 1
  :max-params 1
  :param-types [:subroutine-name]
  :ladsymb :call
  :monitor nil)

(define-instruction "RT"
  :description "Return - Return from subroutine"
  :type :none
  :class :control
  :min-params 0
  :max-params 0
  :ladsymb :return
  :monitor nil)

(define-instruction "RTC"
  :description "Return Conditional - Return if stack is true"
  :type :none
  :class :control
  :min-params 0
  :max-params 0
  :ladsymb :return
  :monitor nil)

(define-instruction "END"
  :description "End - End program execution"
  :type :none
  :class :control
  :min-params 0
  :max-params 0
  :ladsymb :end
  :monitor nil)

(define-instruction "ENDC"
  :description "End Conditional - End if stack is true"
  :type :none
  :class :control
  :min-params 0
  :max-params 0
  :ladsymb :end
  :monitor nil)

(define-instruction "FOR"
  :description "For - Begin for/next loop"
  :type :word
  :class :control
  :min-params 1
  :max-params 1
  :param-types [:word-or-const]
  :ladsymb :for
  :monitor :word)

(define-instruction "NEXT"
  :description "Next - End for/next loop"
  :type :none
  :class :control
  :min-params 0
  :max-params 0
  :ladsymb :next
  :monitor nil)

;;; ============================================================
;;; Special Instructions
;;; ============================================================

(define-instruction "NETWORK"
  :description "Network - Begin new network/rung"
  :type :none
  :class :special
  :min-params 1
  :max-params 1
  :param-types [:network-number]
  :ladsymb nil
  :monitor nil)

(define-instruction "SBR"
  :description "Subroutine - Begin subroutine definition"
  :type :none
  :class :special
  :min-params 1
  :max-params 1
  :param-types [:subroutine-name]
  :ladsymb nil
  :monitor nil)

(define-instruction "SHFRG"
  :description "Shift Register - Shift boolean values through range"
  :type :boolean
  :class :special
  :min-params 2
  :max-params 2
  :param-types [:bool-addr :bool-addr]
  :ladsymb :shfrg
  :monitor :bool)

;;; ============================================================
;;; Instruction Validation
;;; ============================================================

(defn validate-instruction
  "Validate an instruction's parameters.
   Returns [valid? error-message]"
  [instr params]
  (let [count (count params)
        min-p (:min-params instr)
        max-p (:max-params instr)]
    (cond
      (< count min-p)
      [false (format "%s requires at least %d parameter(s), got %d"
                     (:opcode instr) min-p count)]
      (> count max-p)
      [false (format "%s accepts at most %d parameter(s), got %d"
                     (:opcode instr) max-p count)]
      :else
      [true nil])))

;;; ============================================================
;;; Utility Functions
;;; ============================================================

(defn print-instruction-summary
  "Print summary of registered instructions by class"
  []
  (println "\n=== Instruction Set Summary ===")
  (println (format "Total instructions: %d\n" (instruction-count)))
  (doseq [cls [:boolean-input :boolean-output :boolean-compare
               :edge-contact :stack-operation :timer :counter
               :data-move :data-pack :math :search :control :special]]
    (let [instrs (list-instructions-by-class cls)]
      (when (seq instrs)
        (println (format "%s (%d):" cls (count instrs)))
        (println (format "  %s"
                        (clojure.string/join ", "
                                            (map :opcode instrs))))))))
