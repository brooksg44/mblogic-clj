(ns mblogic-clj.compiler
  "IL Compiler - converts parsed instructions to executable code.
   Generates closures for each instruction that operate on a logic stack.
   Ported from: src/compiler.lisp"
  (:require [mblogic-clj.instructions :as instr]
            [mblogic-clj.parser :as parser]
            [mblogic-clj.data-table :as dt]
            [clojure.string :as str]))

;;; ============================================================
;;; Compile Errors
;;; ============================================================

(defn compile-error
  "Create a compile error"
  [message & {:keys [instruction line-number]}]
  {:error-type :compile-error
   :message message
   :instruction instruction
   :line-number line-number})

;;; ============================================================
;;; Execution Context
;;; ============================================================

(defrecord ExecutionContext
  [data-table           ; Data table for variable access
   logic-stack          ; Atom of list (boolean stack)
   stacktop             ; Atom of boolean (current logic result)
   subroutines          ; Map of compiled subroutines
   for-loop-state])     ; Atom for FOR loop tracking

(defn make-execution-context
  "Create a new execution context"
  [data-table subroutines]
  (ExecutionContext. data-table
                     (atom '())
                     (atom false)
                     subroutines
                     (atom {:count 0 :limit 0})))

;;; ============================================================
;;; Value Parsing - Convert IL parameters to runtime values
;;; ============================================================

(defn parse-numeric
  "Parse a numeric parameter"
  [param]
  (try
    (cond
      (str/ends-with? param "h")
      (Long/parseLong (str/replace param #"[hH]$" "") 16)
      (str/includes? param ".")
      (Double/parseDouble param)
      :else
      (Long/parseLong param))
    (catch Exception _ param)))

(defn get-param-value
  "Get the runtime value of a parameter"
  [^ExecutionContext ctx param]
  (cond
    (dt/bool-addr-p param)
    (dt/get-bool (:data-table ctx) param)

    (dt/word-addr-p param)
    (dt/get-word (:data-table ctx) param)

    (dt/float-addr-p param)
    (dt/get-float (:data-table ctx) param)

    (dt/string-addr-p param)
    (dt/get-string (:data-table ctx) param)

    (str/starts-with? param "\"")
    (str/replace param #"^\"|\"$" "")

    :else
    (parse-numeric param)))

;;; ============================================================
;;; Instruction Compilation - Boolean Input
;;; ============================================================

(defn compile-str
  "STR - Store boolean on stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [val (dt/get-bool (:data-table ctx) addr)]
        (swap! (:logic-stack ctx) #(cons val %))
        (reset! (:stacktop ctx) val)))))

(defn compile-strn
  "STRN - Store NOT boolean on stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [val (not (dt/get-bool (:data-table ctx) addr))]
        (swap! (:logic-stack ctx) #(cons val %))
        (reset! (:stacktop ctx) val)))))

(defn compile-and
  "AND - AND with top of stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [new-val (and @(:stacktop ctx) (dt/get-bool (:data-table ctx) addr))]
        (reset! (:stacktop ctx) new-val)
        (swap! (:logic-stack ctx) #(cons new-val (rest %)))))))

(defn compile-andn
  "ANDN - AND NOT with top of stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [new-val (and @(:stacktop ctx) (not (dt/get-bool (:data-table ctx) addr)))]
        (reset! (:stacktop ctx) new-val)
        (swap! (:logic-stack ctx) #(cons new-val (rest %)))))))

(defn compile-or
  "OR - OR with top of stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [new-val (or @(:stacktop ctx) (dt/get-bool (:data-table ctx) addr))]
        (reset! (:stacktop ctx) new-val)
        (swap! (:logic-stack ctx) #(cons new-val (rest %)))))))

(defn compile-orn
  "ORN - OR NOT with top of stack"
  [params]
  (let [addr (first params)]
    (fn [^ExecutionContext ctx]
      (let [new-val (or @(:stacktop ctx) (not (dt/get-bool (:data-table ctx) addr)))]
        (reset! (:stacktop ctx) new-val)
        (swap! (:logic-stack ctx) #(cons new-val (rest %)))))))

;;; ============================================================
;;; Instruction Compilation - Stack Operations
;;; ============================================================

(defn compile-andstr
  "ANDSTR - AND top two stack elements"
  [params]
  (fn [^ExecutionContext ctx]
    (let [stack @(:logic-stack ctx)
          top (first stack)
          second (second stack)
          result (and top second)]
      (swap! (:logic-stack ctx) #(cons result (drop 2 %)))
      (reset! (:stacktop ctx) result))))

(defn compile-orstr
  "ORSTR - OR top two stack elements"
  [params]
  (fn [^ExecutionContext ctx]
    (let [stack @(:logic-stack ctx)
          top (first stack)
          second (second stack)
          result (or top second)]
      (swap! (:logic-stack ctx) #(cons result (drop 2 %)))
      (reset! (:stacktop ctx) result))))

;;; ============================================================
;;; Instruction Compilation - Boolean Output
;;; ============================================================

(defn compile-out
  "OUT - Output stacktop to address(es)"
  [params]
  (fn [^ExecutionContext ctx]
    (doseq [addr params]
      (dt/set-bool (:data-table ctx) addr @(:stacktop ctx)))))

(defn compile-set
  "SET - Latch address(es) if stacktop is true"
  [params]
  (fn [^ExecutionContext ctx]
    (when @(:stacktop ctx)
      (doseq [addr params]
        (dt/set-bool (:data-table ctx) addr true)))))

(defn compile-rst
  "RST - Reset address(es) if stacktop is true"
  [params]
  (fn [^ExecutionContext ctx]
    (when @(:stacktop ctx)
      (doseq [addr params]
        (dt/set-bool (:data-table ctx) addr false)))))

(defn compile-pd
  "PD - Pulse/differentiate output on rising edge"
  [params]
  (fn [^ExecutionContext ctx]
    (doseq [addr params]
      (let [edge-key (str addr "-pd")
            prev (get-in @(:for-loop-state ctx) [:edge-state edge-key])
            current @(:stacktop ctx)
            is-rising-edge (and current (not prev))]
        (when is-rising-edge
          (dt/set-bool (:data-table ctx) addr true))
        (when (and (not current) prev)
          (dt/set-bool (:data-table ctx) addr false))
        (swap! (:for-loop-state ctx) assoc-in [:edge-state edge-key] current)))))

;;; ============================================================
;;; Instruction Compilation - Comparisons
;;; ============================================================

(defn compile-stre
  "STRE - Store Equal - Compare for equality"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (= v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

(defn compile-strne
  "STRNE - Store Not Equal"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (not= v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

(defn compile-strgt
  "STRGT - Store Greater Than"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (> v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

(defn compile-strlt
  "STRLT - Store Less Than"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (< v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

(defn compile-strge
  "STRGE - Store Greater/Equal"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (>= v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

(defn compile-strle
  "STRLE - Store Less/Equal"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            result (<= v1 v2)]
        (swap! (:logic-stack ctx) #(cons result %))
        (reset! (:stacktop ctx) result)))))

;;; ============================================================
;;; AND Comparison Variants
;;; ============================================================

(defn compile-ande
  "ANDE - AND stack with equality"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (= v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-andne
  "ANDNE - AND stack with inequality"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (not= v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-andgt
  "ANDGT - AND stack with >"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (> v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-andlt
  "ANDLT - AND stack with <"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (< v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-andge
  "ANDGE - AND stack with >="
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (>= v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-andle
  "ANDLE - AND stack with <="
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (<= v1 v2)
            result (and @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

;;; ============================================================
;;; OR Comparison Variants (similar pattern)
;;; ============================================================

(defn compile-ore
  "ORE - OR stack with equality"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (= v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-orne
  "ORNE - OR stack with inequality"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (not= v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-orgt
  "ORGT - OR stack with >"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (> v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-orlt
  "ORLT - OR stack with <"
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (< v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-orge
  "ORGE - OR stack with >="
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (>= v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

(defn compile-orle
  "ORLE - OR stack with <="
  [params]
  (let [val1 (first params)
        val2 (second params)]
    (fn [^ExecutionContext ctx]
      (let [v1 (get-param-value ctx val1)
            v2 (get-param-value ctx val2)
            cmp (<= v1 v2)
            result (or @(:stacktop ctx) cmp)]
        (reset! (:stacktop ctx) result)
        (swap! (:logic-stack ctx) #(cons result (rest %)))))))

;;; ============================================================
;;; Instruction Dispatch
;;; ============================================================

(defn compile-instruction
  "Compile a parsed instruction to an executable closure"
  [parsed-instr]
  (let [opcode (:opcode parsed-instr)
        params (:params parsed-instr)]
    (case opcode
      "STR" (compile-str params)
      "STRN" (compile-strn params)
      "AND" (compile-and params)
      "ANDN" (compile-andn params)
      "OR" (compile-or params)
      "ORN" (compile-orn params)
      "ANDSTR" (compile-andstr params)
      "ORSTR" (compile-orstr params)
      "OUT" (compile-out params)
      "SET" (compile-set params)
      "RST" (compile-rst params)
      "PD" (compile-pd params)
      "STRE" (compile-stre params)
      "STRNE" (compile-strne params)
      "STRGT" (compile-strgt params)
      "STRLT" (compile-strlt params)
      "STRGE" (compile-strge params)
      "STRLE" (compile-strle params)
      "ANDE" (compile-ande params)
      "ANDNE" (compile-andne params)
      "ANDGT" (compile-andgt params)
      "ANDLT" (compile-andlt params)
      "ANDGE" (compile-andge params)
      "ANDLE" (compile-andle params)
      "ORE" (compile-ore params)
      "ORNE" (compile-orne params)
      "ORGT" (compile-orgt params)
      "ORLT" (compile-orlt params)
      "ORGE" (compile-orge params)
      "ORLE" (compile-orle params)
      nil)))  ; Unknown instruction

;;; ============================================================
;;; Network Compilation
;;; ============================================================

(defn compile-network
  "Compile a parsed network to a list of closures"
  [network]
  (->> (:instructions network)
       (map compile-instruction)
       (filter identity)
       vec))

;;; ============================================================
;;; Program Compilation
;;; ============================================================

(defn compile-program
  "Compile a parsed program to executable form.
   Returns: {:main-fn (fn [data-table]) :subroutines {...}}"
  [parsed-program]
  (let [networks (:main-networks parsed-program)
        subroutines (:subroutines parsed-program)

        ;; Compile main program
        network-closures (vec (for [network networks]
                                (let [closures (compile-network network)]
                                  (fn [ctx]
                                    ;; Clear stack at start of network
                                    (reset! (:logic-stack ctx) '())
                                    (reset! (:stacktop ctx) false)
                                    ;; Execute all instructions in network
                                    (doseq [closure closures]
                                      (closure ctx))))))
        main-fn (fn [data-table]
                  (let [ctx (make-execution-context data-table {})]
                    (doseq [net-fn network-closures]
                      (net-fn ctx))))

        ;; Compile subroutines
        subroutine-fns (reduce (fn [acc [name sbr]]
                                 (let [closures (mapcat #(compile-network %) (:networks sbr))
                                       sbr-fn (fn [data-table]
                                                (let [ctx (make-execution-context data-table {})]
                                                  (doseq [closure closures]
                                                    (closure ctx))))]
                                   (assoc acc name sbr-fn)))
                               {}
                               subroutines)]

    {:main-fn main-fn
     :subroutines subroutine-fns
     :parsed-program parsed-program}))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn compile-il-string
  "Parse and compile an IL string"
  [source]
  (let [parsed (parser/parse-il-string source)]
    (compile-program parsed)))

(defn compile-il-file
  "Parse and compile an IL file"
  [filename]
  (let [parsed (parser/parse-il-file filename)]
    (compile-program parsed)))
