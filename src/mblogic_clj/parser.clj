(ns mblogic-clj.parser
  "IL Parser - converts IL text to parsed instructions.
   Handles tokenization, instruction parsing, and network extraction.
   Ported from: src/parser.lisp"
  (:require [mblogic-clj.instructions :as instr]
            [clojure.string :as str]))

;;; ============================================================
;;; Parse Error
;;; ============================================================

(defn parse-error
  "Create a parse error with optional line information"
  [message & {:keys [line-number line-text]}]
  {:error-type :parse-error
   :message message
   :line-number line-number
   :line-text line-text})

(defn error?
  "Check if item is a parse error"
  [item]
  (and (map? item) (= :parse-error (:error-type item))))

;;; ============================================================
;;; Parsed Structures
;;; ============================================================

(defrecord ParsedInstruction
  [opcode       ; Instruction mnemonic
   params       ; List of parameter strings
   line-number  ; Source line number
   instruction-def ; Reference to instruction definition (or nil)
   comment])    ; Associated comment (or nil)

(defrecord ParsedNetwork
  [number       ; Network number
   instructions ; List of parsed instructions
   comments])   ; Comments preceding the network

(defrecord ParsedSubroutine
  [name         ; Subroutine name
   networks     ; List of networks
   line-number])

(defrecord ParsedProgram
  [main-networks ; Networks in main program
   subroutines   ; Map of subroutine-name -> ParsedSubroutine
   errors        ; List of parse errors
   warnings])    ; List of warnings

;;; ============================================================
;;; String Utilities
;;; ============================================================

(defn trim-whitespace
  "Remove leading and trailing whitespace"
  [str]
  (str/trim str))

(defn comment-line?
  "Check if line is a comment (starts with //)"
  [line]
  (let [trimmed (trim-whitespace line)]
    (str/starts-with? trimmed "//")))

(defn blank-line?
  "Check if line is blank"
  [line]
  (empty? (trim-whitespace line)))

(defn extract-comment
  "Extract comment text from a comment line"
  [line]
  (let [trimmed (trim-whitespace line)]
    (if (str/starts-with? trimmed "//")
      (trim-whitespace (subs trimmed 2))
      nil)))

;;; ============================================================
;;; Tokenization
;;; ============================================================

(defn split-into-lines
  "Split source string into lines"
  [source]
  (if (string? source)
    (str/split source #"\r?\n")
    source))

(defn tokenize-line
  "Tokenize a line into opcode and parameters.
   Handles quoted strings and preserves expression structure."
  [line]
  (let [trimmed (trim-whitespace line)]
    (if (empty? trimmed)
      []
      (loop [i 0
             len (count trimmed)
             tokens []
             current-token (StringBuilder.)
             in-quotes false
             paren-depth 0]
        (if (>= i len)
          ;; Add final token if any
          (let [final (str current-token)]
            (if (empty? final)
              tokens
              (conj tokens final)))
          ;; Process character
          (let [c (nth trimmed i)]
            (cond
              ;; Handle quoted strings
              (= c \")
              (if in-quotes
                ;; End quote
                (do
                  (.append current-token c)
                  (recur (inc i) len tokens current-token false paren-depth))
                ;; Start quote - push current token first
                (let [tokens' (if (> (.length current-token) 0)
                                (conj tokens (str current-token))
                                tokens)]
                  (.setLength current-token 0)
                  (.append current-token c)
                  (recur (inc i) len tokens' current-token true paren-depth)))

              ;; Inside quotes - take everything
              in-quotes
              (do
                (.append current-token c)
                (recur (inc i) len tokens current-token true paren-depth))

              ;; Track parentheses depth
              (= c \()
              (do
                (.append current-token c)
                (recur (inc i) len tokens current-token in-quotes (inc paren-depth)))

              (= c \))
              (do
                (.append current-token c)
                (recur (inc i) len tokens current-token in-quotes (dec paren-depth)))

              ;; Whitespace outside quotes/parens splits tokens
              (and (or (= c \space) (= c \tab)) (zero? paren-depth))
              (let [tokens' (if (> (.length current-token) 0)
                              (conj tokens (str current-token))
                              tokens)]
                (.setLength current-token 0)
                (recur (inc i) len tokens' current-token in-quotes paren-depth))

              ;; Regular character
              :else
              (do
                (.append current-token c)
                (recur (inc i) len tokens current-token in-quotes paren-depth)))))))

(defn parse-math-expression
  "Combine tokens after MATHDEC/MATHHEX into expression string"
  [tokens]
  (if (>= (count tokens) 3)
    (let [dest (first tokens)
          flags (second tokens)
          expr (str/join " " (drop 2 tokens))]
      [dest flags expr])
    tokens))

;;; ============================================================
;;; Instruction Parsing
;;; ============================================================

(defn parse-instruction-line
  "Parse a single instruction line into a ParsedInstruction"
  [line line-number]
  (let [tokens (tokenize-line line)
        opcode (when (seq tokens) (str/upper-case (first tokens)))
        params (rest tokens)]

    (if-not opcode
      nil
      ;; Look up instruction definition
      (let [instr-def (instr/find-instruction opcode)
            ;; Special handling for MATHDEC/MATHHEX
            params' (if (and instr-def
                            (some #{opcode} ["MATHDEC" "MATHHEX"])
                            (>= (count params) 2))
                      (parse-math-expression params)
                      (vec params))]
        (ParsedInstruction. opcode params' line-number instr-def nil)))))

;;; ============================================================
;;; IL Parser State and Methods
;;; ============================================================

(defrecord ILParser
  [^:unsynchronized-mutable lines
   ^:unsynchronized-mutable current-line
   ^:unsynchronized-mutable errors
   ^:unsynchronized-mutable warnings
   ^:unsynchronized-mutable pending-comments])

(defn make-il-parser
  "Create a new IL parser"
  [source]
  (let [lines (split-into-lines source)]
    (ILParser. lines 0 [] [] [])))

(defn peek-line
  "Look at current line without advancing"
  [^ILParser parser]
  (let [idx (.current-line parser)]
    (when (< idx (count (.lines parser)))
      (nth (.lines parser) idx))))

(defn next-line
  "Get current line and advance"
  [^ILParser parser]
  (let [line (peek-line parser)]
    (when line
      (set! (.current-line parser) (inc (.current-line parser))))
    line))

(defn current-line-number
  "Get 1-based current line number"
  [^ILParser parser]
  (inc (.current-line parser)))

(defn add-error
  "Add a parse error"
  [^ILParser parser message & {:keys [line-text]}]
  (set! (.errors parser)
        (conj (.errors parser)
              (parse-error message
                          :line-number (current-line-number parser)
                          :line-text line-text))))

(defn add-warning
  "Add a parse warning"
  [^ILParser parser message]
  (set! (.warnings parser)
        (conj (.warnings parser)
              {:line-number (current-line-number parser)
               :message message})))

(defn collect-comments
  "Collect consecutive comment lines"
  [^ILParser parser]
  (loop [comments []]
    (if (and (peek-line parser) (comment-line? (peek-line parser)))
      (let [comment (extract-comment (next-line parser))]
        (recur (conj comments comment)))
      comments)))

(defn skip-blank-lines
  "Skip blank lines"
  [^ILParser parser]
  (while (and (peek-line parser) (blank-line? (peek-line parser)))
    (next-line parser)))

;;; ============================================================
;;; Network Parsing
;;; ============================================================

(defn parse-network
  "Parse a network's instructions until next NETWORK, SBR, or end"
  [^ILParser parser network-number]
  (let [initial-comments (vec (.pending-comments parser))]
    (set! (.pending-comments parser) [])

    (loop [instructions []]
      (skip-blank-lines parser)

      (if-not (peek-line parser)
        ;; End of input
        (ParsedNetwork. network-number (vec (reverse instructions)) initial-comments)

        (let [line (peek-line parser)
              trimmed (trim-whitespace line)]

          (cond
            ;; Comment line
            (comment-line? line)
            (let [comment (extract-comment (next-line parser))]
              (set! (.pending-comments parser)
                    (conj (.pending-comments parser) comment))
              (recur instructions))

            ;; Blank line
            (blank-line? line)
            (do
              (next-line parser)
              (recur instructions))

            ;; NETWORK or SBR - end current network
            (or (re-matches #"(?i)^NETWORK\s.*" trimmed)
                (re-matches #"(?i)^SBR\s.*" trimmed))
            (ParsedNetwork. network-number (vec (reverse instructions)) initial-comments)

            ;; Instruction line
            :else
            (let [line-num (current-line-number parser)
                  parsed (parse-instruction-line (next-line parser) line-num)]

              (cond
                ;; Null parsed (empty line after trim)
                (nil? parsed)
                (recur instructions)

                ;; Unknown instruction
                (nil? (:instruction-def parsed))
                (do
                  (add-error parser
                            (format "Unknown instruction: %s" (:opcode parsed))
                            :line-text line)
                  (recur instructions))

                ;; Valid instruction
                :else
                (let [parsed' (if (seq (.pending-comments parser))
                               (assoc parsed :comment
                                     (str/join "\n" (.pending-comments parser)))
                               parsed)]
                  (set! (.pending-comments parser) [])

                  ;; Validate parameter count
                  (let [[valid? error-msg] (instr/validate-instruction
                                           (:instruction-def parsed')
                                           (:params parsed'))]
                    (when-not valid?
                      (add-warning parser error-msg)))

                  (recur (conj instructions parsed')))))))))))

;;; ============================================================
;;; Subroutine Parsing
;;; ============================================================

(defn parse-subroutine
  "Parse a subroutine definition"
  [^ILParser parser name line-number]
  (loop [networks []]
    (skip-blank-lines parser)

    ;; Collect comments
    (let [comments (collect-comments parser)]
      (when (seq comments)
        (set! (.pending-comments parser)
              (into (.pending-comments parser) comments))))

    (if-not (peek-line parser)
      ;; End of input
      (ParsedSubroutine. name (vec (reverse networks)) line-number)

      (let [line (peek-line parser)
            trimmed (trim-whitespace line)]

        (cond
          ;; Another SBR - end this subroutine
          (re-matches #"(?i)^SBR\s.*" trimmed)
          (ParsedSubroutine. name (vec (reverse networks)) line-number)

          ;; NETWORK declaration
          (re-matches #"(?i)^NETWORK\s+(\d+)" trimmed)
          (let [match (re-find #"(?i)^NETWORK\s+(\d+)" trimmed)
                net-num (parse-long (second match))]
            (next-line parser)
            (let [net (parse-network parser net-num)]
              (recur (conj networks net))))

          ;; Something else before first NETWORK
          :else
          (do
            (add-warning parser
                        (format "Instruction outside NETWORK in subroutine %s" name))
            (next-line parser)
            (recur networks)))))))

;;; ============================================================
;;; Program Parsing
;;; ============================================================

(defn parse-program
  "Parse a complete IL program"
  [^ILParser parser]
  (set! (.current-line parser) 0)
  (set! (.errors parser) [])
  (set! (.warnings parser) [])
  (set! (.pending-comments parser) [])

  (loop [main-networks []
         subroutines {}]
    (skip-blank-lines parser)

    ;; Collect comments
    (let [comments (collect-comments parser)]
      (when (seq comments)
        (set! (.pending-comments parser)
              (into (.pending-comments parser) comments))))

    (skip-blank-lines parser)

    (if-not (peek-line parser)
      ;; End of input
      (ParsedProgram. (vec (reverse main-networks))
                     subroutines
                     (vec (.errors parser))
                     (vec (.warnings parser)))

      (let [line (peek-line parser)
            trimmed (trim-whitespace line)]

        (cond
          ;; SBR declaration
          (re-matches #"(?i)^SBR\s+(\S+)" trimmed)
          (let [match (re-find #"(?i)^SBR\s+(\S+)" trimmed)
                sbr-name (second match)
                line-num (current-line-number parser)]
            (next-line parser)
            (let [sbr (parse-subroutine parser sbr-name line-num)]
              (recur main-networks (assoc subroutines sbr-name sbr))))

          ;; NETWORK declaration
          (re-matches #"(?i)^NETWORK\s+(\d+)" trimmed)
          (let [match (re-find #"(?i)^NETWORK\s+(\d+)" trimmed)
                net-num (parse-long (second match))]
            (next-line parser)
            (let [net (parse-network parser net-num)]
              (recur (conj main-networks net) subroutines)))

          ;; Something else at top level
          :else
          (do
            (add-warning parser (format "Unexpected content at top level: %s" trimmed))
            (next-line parser)
            (recur main-networks subroutines)))))))

;;; ============================================================
;;; Public API
;;; ============================================================

(defn parse-il-string
  "Parse IL program from a string.
   Returns: ParsedProgram record"
  [source]
  (let [parser (make-il-parser source)]
    (parse-program parser)))

(defn parse-il-file
  "Parse IL program from a file.
   Returns: ParsedProgram record"
  [filename]
  (parse-il-string (slurp filename)))

;;; ============================================================
;;; Utility Functions
;;; ============================================================

(defn count-instructions
  "Count total instructions in a parsed program"
  [program]
  (let [main-count (reduce + 0 (map #(count (:instructions %))
                                    (:main-networks program)))
        subroutine-count (reduce + 0 (map #(reduce + 0
                                                    (map (fn [net]
                                                           (count (:instructions net)))
                                                         (:networks %)))
                                          (vals (:subroutines program))))]
    (+ main-count subroutine-count)))

(defn print-program-summary
  "Print a summary of a parsed program"
  [program]
  (println "\n=== Parsed Program Summary ===")

  ;; Errors
  (when (seq (:errors program))
    (println (format "\nERRORS (%d):" (count (:errors program))))
    (doseq [err (:errors program)]
      (println (format "  Line %s: %s"
                      (:line-number err)
                      (:message err)))))

  ;; Warnings
  (when (seq (:warnings program))
    (println (format "\nWARNINGS (%d):" (count (:warnings program))))
    (doseq [warn (:warnings program)]
      (println (format "  Line %s: %s"
                      (:line-number warn)
                      (:message warn)))))

  ;; Main program
  (println "\nMAIN PROGRAM:")
  (println (format "  Networks: %d" (count (:main-networks program))))
  (doseq [net (:main-networks program)]
    (println (format "    Network %d: %d instructions"
                    (:number net)
                    (count (:instructions net)))))

  ;; Subroutines
  (println (format "\nSUBROUTINES (%d):" (count (:subroutines program))))
  (doseq [[name sbr] (:subroutines program)]
    (println (format "  %s: %d networks" name (count (:networks sbr))))))

(defn print-network-detail
  "Print detailed view of a network"
  [network]
  (println (format "Network %d:" (:number network)))
  (doseq [instr (:instructions network)]
    (println (format "  %4d: %s%s"
                    (:line-number instr)
                    (:opcode instr)
                    (if (seq (:params instr))
                      (str " " (str/join " " (:params instr)))
                      "")))))
