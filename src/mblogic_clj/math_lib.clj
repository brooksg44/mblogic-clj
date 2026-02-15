(ns mblogic-clj.math-lib
  "Math library for PLC operations.
   Implements MATHDEC (decimal), MATHHEX (hexadecimal/bitwise), and utilities.
   Ported from: src/math-lib.lisp"
  (:require [mblogic-clj.data-table :as dt]
            [clojure.string :as str]))

;;; ============================================================
;;; Expression Tokenization
;;; ============================================================

(defn tokenize-expr
  "Tokenize a mathematical expression into tokens
   Handles: numbers, variables, operators, functions, parentheses"
  [expr]
  (let [expr (str/trim expr)]
    (loop [i 0
           tokens []
           current-token []]
      (if (>= i (count expr))
        ;; Add final token if any
        (if (seq current-token)
          (conj tokens (apply str current-token))
          tokens)

        (let [ch (get expr i)]
          (cond
            ;; Whitespace - end token
            (Character/isWhitespace ch)
            (let [tokens' (if (seq current-token)
                            (conj tokens (apply str current-token))
                            tokens)]
              (recur (inc i) tokens' []))

            ;; Single-char operators and delimiters
            (contains? #{'+' '-' '*' \/ '%' '^' '(' ')' ','} ch)
            (let [tokens' (if (seq current-token)
                            (conj tokens (apply str current-token))
                            tokens)]
              (recur (inc i) (conj tokens' (str ch)) []))

            ;; Comparison operators (may be two chars)
            (contains? #{'<' '>' '=' '!'} ch)
            (let [next-ch (when (< (inc i) (count expr)) (get expr (inc i)))
                  tokens' (if (seq current-token)
                            (conj tokens (apply str current-token))
                            tokens)
                  op (if (= next-ch '=)
                      (str ch next-ch)
                      (str ch))]
              (if (= next-ch '=)
                (recur (+ i 2) (conj tokens' op) [])
                (recur (inc i) (conj tokens' op) [])))

            ;; Everything else (identifiers, numbers)
            :else
            (recur (inc i) tokens (conj current-token ch)))))))

;;; ============================================================
;;; Expression Parser (Infix to evaluation)
;;; ============================================================

(declare parse-expr)

(defn parse-primary
  "Parse primary expression (number, variable, parenthesized expr)"
  [tokens pos]
  (if (>= pos (count tokens))
    [nil pos]

    (let [tok (nth tokens pos)]
      (cond
        ;; Parenthesized expression
        (= tok "(")
        (let [[result pos'] (parse-expr tokens (inc pos))]
          (if (and (< pos' (count tokens)) (= (nth tokens pos') ")"))
            [result (inc pos')]
            [result pos']))

        ;; Negative number
        (= tok "-")
        (let [[result pos'] (parse-primary tokens (inc pos))]
          [(if (number? result) (- result) result) pos'])

        ;; Number literal or variable name
        :else
        (try
          [(Double/parseDouble tok) (inc pos)]
          (catch Exception _
            ;; It's a variable name - leave as string
            [tok (inc pos)]))))))

(defn parse-expr
  "Parse expression with operator precedence"
  [tokens pos]
  (let [[left pos'] (parse-primary tokens pos)]
    (loop [result left
           pos pos']
      (if (>= pos (count tokens))
        [result pos]

        (let [op (nth tokens pos)]
          (cond
            ;; End of expression markers
            (contains? #{")" ","} op)
            [result pos]

            ;; Binary operators
            (contains? #{"+", "-", "*", "/", "%", "^", "<", ">", "=", "!=", "<=", ">="} op)
            (let [[right pos''] (parse-primary tokens (inc pos))]
              (case op
                "+" (recur (+ result right) pos'')
                "-" (recur (- result right) pos'')
                "*" (recur (* result right) pos'')
                "/" (recur (if (zero? right) 0 (/ result right)) pos'')
                "%" (recur (mod (long result) (long right)) pos'')
                "^" (recur (Math/pow result right) pos'')
                "<" (recur (if (< result right) 1 0) pos'')
                ">" (recur (if (> result right) 1 0) pos'')
                "=" (recur (if (= result right) 1 0) pos'')
                "!=" (recur (if (not= result right) 1 0) pos'')
                "<=" (recur (if (<= result right) 1 0) pos'')
                ">=" (recur (if (>= result right) 1 0) pos'')
                (recur result pos'')))

            :else
            [result pos])))))

(defn evaluate-expr
  "Evaluate a math expression string
   Supports: +, -, *, /, %, ^ operators and numeric literals"
  [expr]
  (try
    (let [tokens (tokenize-expr expr)
          [result _] (parse-expr tokens 0)]
      (if (number? result)
        result
        0))
    (catch Exception _
      0)))

;;; ============================================================
;;; MATHDEC - Decimal Math
;;; ============================================================

(defn mathdec-execute
  "Execute MATHDEC instruction (decimal arithmetic)

   Parameters:
   - data-table: PLC data table
   - dest-addr: Destination address for result
   - flags: Flags (unused for now)
   - expr-string: Mathematical expression (decimal numbers)

   Returns: Result value stored in destination"
  [data-table dest-addr flags expr-string]
  (try
    (let [result (evaluate-expr expr-string)
          ;; Convert to integer if destination is word
          int-result (if (dt/word-addr-p dest-addr)
                       (long result)
                       result)]
      (dt/set-value data-table dest-addr int-result)
      int-result)

    (catch Exception e
      (do
        (.log js/console (str "MATHDEC error: " e))
        0))))

;;; ============================================================
;;; MATHHEX - Hexadecimal/Bitwise Math
;;; ============================================================

(defn hex-evaluate-expr
  "Evaluate expression in hex/bitwise mode
   Supports: +, -, *, /, &, |, ^, <<, >> operators"
  [expr]
  (try
    (let [tokens (tokenize-expr expr)]
      ;; Parse hex numbers (0x prefix)
      (let [hex-tokens (map (fn [tok]
                              (if (str/starts-with? tok "0x")
                                (Long/parseLong (subs tok 2) 16)
                                (try
                                  (Long/parseLong tok)
                                  (catch Exception _ tok))))
                            tokens)
            [result _] (loop [[head & tail] hex-tokens
                              pos 0]
                         (if (nil? head)
                           [0 pos]
                           (let [op (when (seq tail) (second tail))]
                             (case op
                               "&" [(bit-and head (nth (rest tail) 0 0)) (+ pos 3)]
                               "|" [(bit-or head (nth (rest tail) 0 0)) (+ pos 3)]
                               "^" [(bit-xor head (nth (rest tail) 0 0)) (+ pos 3)]
                               "<<" [(bit-shift-left head (nth (rest tail) 0 0)) (+ pos 3)]
                               ">>" [(bit-shift-right head (nth (rest tail) 0 0)) (+ pos 3)]
                               [head (inc pos)]))))]
        result))
    (catch Exception _
      0)))

(defn mathhex-execute
  "Execute MATHHEX instruction (hexadecimal/bitwise arithmetic)

   Parameters:
   - data-table: PLC data table
   - dest-addr: Destination address for result
   - flags: Flags (unused for now)
   - expr-string: Mathematical expression (hex numbers with 0x prefix, bitwise operators)

   Returns: Result value stored in destination"
  [data-table dest-addr flags expr-string]
  (try
    (let [result (hex-evaluate-expr expr-string)]
      (dt/set-value data-table dest-addr result)
      result)

    (catch Exception e
      (do
        (.log js/console (str "MATHHEX error: " e))
        0))))

;;; ============================================================
;;; Utility Functions
;;; ============================================================

(defn parse-numeric
  "Parse a numeric value from string (decimal or hex with 0x prefix)"
  [s]
  (try
    (if (str/starts-with? s "0x")
      (Long/parseLong (subs s 2) 16)
      (Double/parseDouble s))
    (catch Exception _
      0)))

;;; End of math-lib.clj
