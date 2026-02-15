(ns mblogic-clj.web.ladder-render
  "Ladder diagram rendering engine.
   Converts compiled programs to ladder diagram JSON representation.
   CRITICAL: Must produce output compatible with frontend rendering."
  (:require [mblogic-clj.instructions :as instr]
            [clojure.string :as str]))

;;; ============================================================
;;; Ladder Symbol Classes
;;; ============================================================

(def symbol-classes
  "Mapping of instruction types to ladder diagram symbols"
  {:boolean-input {:class "input" :width 2 :height 1}
   :boolean-output {:class "output" :width 2 :height 1}
   :contact-series {:class "contact-series" :width 1 :height 1}
   :contact-parallel {:class "contact-parallel" :width 1 :height 1}
   :coil-normal {:class "coil-normal" :width 2 :height 1}
   :coil-set {:class "coil-set" :width 2 :height 1}
   :coil-reset {:class "coil-reset" :width 2 :height 1}
   :comparison {:class "comparison" :width 3 :height 2}
   :timer {:class "timer" :width 3 :height 2}
   :counter {:class "counter" :width 3 :height 2}
   :function-block {:class "function-block" :width 4 :height 3}})

;;; ============================================================
;;; Rung Symbol Generation
;;; ============================================================

(defn instruction-to-symbol
  "Convert an instruction to a ladder diagram symbol"
  [instruction]
  (try
    (let [{:keys [opcode params]} instruction
          instr-def (instr/find-instruction opcode)
          instr-type (when instr-def (:type instr-def))]
      {:opcode opcode
       :params params
       :type (or instr-type :unknown)
       :class (get-in symbol-classes [instr-type :class] "unknown")})
    (catch Exception e
      {:opcode "ERROR"
       :params []
       :error (str e)})))

(defn build-rung
  "Build a single rung from instructions"
  [instructions]
  (try
    {:symbols (mapv instruction-to-symbol instructions)
     :instruction-count (count instructions)}
    (catch Exception e
      {:error (str "Failed to build rung: " e)})))

(defn network-to-rungs
  "Convert a network to ladder rungs"
  [network]
  (try
    (let [instructions (:instructions network)
          rungs (if (seq instructions)
                  [(build-rung instructions)]
                  [])]
      {:network-number (:number network)
       :rungs rungs
       :rung-count (count rungs)})
    (catch Exception e
      {:error (str "Failed to convert network: " e)})))

;;; ============================================================
;;; Program to Ladder Conversion
;;; ============================================================

(defn main-networks-to-ladder
  "Convert main program networks to ladder representation"
  [main-networks]
  (try
    (mapv network-to-rungs main-networks)
    (catch Exception e
      [{:error (str "Failed to convert main networks: " e)}])))

(defn subroutine-to-ladder
  "Convert a subroutine to ladder representation"
  [name subroutine]
  (try
    {:name name
     :networks (mapv network-to-rungs (:networks subroutine))}
    (catch Exception e
      {:name name
       :error (str "Failed to convert subroutine: " e)})))

(defn subroutines-to-ladder
  "Convert all subroutines to ladder representation"
  [subroutines]
  (try
    (into {}
          (map (fn [[name sbr]]
                 [name (subroutine-to-ladder name sbr)])
               subroutines))
    (catch Exception e
      {:error (str "Failed to convert subroutines: " e)})))

;;; ============================================================
;;; Main Entry Point
;;; ============================================================

(defn program-to-ladder-json
  "Convert compiled program to ladder diagram JSON.

   Returns: JSON structure with:
   - main: Array of networks with their rungs
   - subroutines: Map of subroutine names to ladder representations
   - metadata: Information about the program structure"
  [program]
  (try
    (when-not program
      (throw (Exception. "No program provided")))

    {:main (main-networks-to-ladder (:main-networks program))
     :subroutines (subroutines-to-ladder (:subroutines program))
     :metadata {:total-networks (count (:main-networks program))
                :total-subroutines (count (:subroutines program))
                :symbol-classes (keys symbol-classes)}}

    (catch Exception e
      {:error (str "Failed to convert program to ladder: " e)
       :stack-trace (str (Throwable->map e))})))

;;; ============================================================
;;; Ladder Diagram Utilities
;;; ============================================================

(defn get-rung-dimensions
  "Calculate dimensions for a rung based on symbols"
  [rung]
  (try
    (let [symbols (:symbols rung)
          total-width (reduce + 0 (map #(get-in symbol-classes [(:type %) :width] 1)
                                       symbols))
          max-height (reduce max 1 (map #(get-in symbol-classes [(:type %) :height] 1)
                                        symbols))]
      {:width total-width :height max-height})
    (catch Exception _
      {:width 10 :height 2})))

(defn ladder-statistics
  "Generate statistics about a ladder diagram"
  [ladder-json]
  (try
    (let [main (:main ladder-json)
          total-rungs (reduce + 0 (map #(count (:rungs %)) main))
          subs (:subroutines ladder-json)
          sub-rungs (reduce + 0 (map #(reduce + 0 (map (fn [n] (count (:rungs n)))
                                                         (:networks %)))
                                      (vals subs)))]
      {:total-main-rungs total-rungs
       :total-sub-rungs sub-rungs
       :total-rungs (+ total-rungs sub-rungs)
       :subroutine-count (count subs)})
    (catch Exception _
      {})))

;;; End of ladder-render.clj
