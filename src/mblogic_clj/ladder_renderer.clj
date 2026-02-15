(ns mblogic-clj.ladder-renderer
  "Convert IL programs to ladder diagram representation.
   Ladder diagrams are a visual representation of relay logic,
   commonly used in industrial automation."
  (:require [clojure.string :as str]))

;;; ============================================================
;;; Ladder Diagram Data Structures
;;; ============================================================

(defrecord LadderRung [network-id instructions ladder-form il-form])
(defrecord LadderDiagram [networks subroutines])

;;; ============================================================
;;; Ladder Instruction Classification
;;; ============================================================

(def ladder-instructions
  "Instructions that can be directly represented in ladder logic"
  {
   ;; Contact instructions
   "STR" {:type :series-contact :input true}
   "STRN" {:type :series-contact-not :input true}
   "AND" {:type :series-contact :input true}
   "ANDN" {:type :series-contact-not :input true}
   "OR" {:type :parallel-contact :input true}
   "ORN" {:type :parallel-contact-not :input true}
   
   ;; Edge detection
   "STRPD" {:type :series-contact-pd :input true}
   "STRND" {:type :series-contact-nd :input true}
   "ANDPD" {:type :series-contact-pd :input true}
   "ANDND" {:type :series-contact-nd :input true}
   "ORPD" {:type :parallel-contact-pd :input true}
   "ORND" {:type :parallel-contact-nd :input true}
   
   ;; Comparisons
   "STRE" {:type :compare-eq :input true}
   "STRNE" {:type :compare-ne :input true}
   "STRGT" {:type :compare-gt :input true}
   "STRLT" {:type :compare-lt :input true}
   "STRGE" {:type :compare-ge :input true}
   "STRLE" {:type :compare-le :input true}
   
   "ANDE" {:type :compare-eq :input true}
   "ANDNE" {:type :compare-ne :input true}
   "ANDGT" {:type :compare-gt :input true}
   "ANDLT" {:type :compare-lt :input true}
   "ANDGE" {:type :compare-ge :input true}
   "ANDLE" {:type :compare-le :input true}
   
   "ORE" {:type :compare-eq :input true}
   "ORNE" {:type :compare-ne :input true}
   "ORGT" {:type :compare-gt :input true}
   "ORLT" {:type :compare-lt :input true}
   "ORGE" {:type :compare-ge :input true}
   "ORLE" {:type :compare-le :input true}
   
   ;; Coil instructions (outputs)
   "OUT" {:type :coil :output true}
   "SET" {:type :set-coil :output true}
   "RST" {:type :reset-coil :output true}
   "PD" {:type :pulse-coil :output true}
   
   ;; Stack operations
   "ANDSTR" {:type :stack-and :stack true}
   "ORSTR" {:type :stack-or :stack true}
   })

(def non-ladder-instructions
  "Instructions that cannot be directly represented in ladder logic"
  {"COPY" true
   "CPYBLK" true
   "FILL" true
   "PACK" true
   "UNPACK" true
   "MATHDEC" true
   "MATHHEX" true
   "FINDEQ" true
   "FINDNE" true
   "FINDGT" true
   "FINDLT" true
   "FINDGE" true
   "FINDLE" true
   "FINDIEQ" true
   "FINDINE" true
   "FINDIGT" true
   "FINDILT" true
   "FINDIGE" true
   "FINDILE" true
   "SHFRG" true
   "TMR" true
   "TMRA" true
   "TMROFF" true
   "CNTU" true
   "CNTD" true
   "UDC" true
   "CALL" true
   "RTC" true
   "RT" true
   "FOR" true
   "NEXT" true
   "END" true
   "ENDC" true
   "NETWORK" true
   "SUM" true})

;;; ============================================================
;;; Ladder Rung Analysis
;;; ============================================================

(defn can-render-as-ladder?
  "Check if a rung can be represented as a ladder diagram"
  [instructions]
  (let [instr-names (map (fn [i] (or (:name i) (:opcode i))) instructions)]
    (and
     ;; Must have at least one input contact (STR, AND, OR, etc.)
     (some (fn [name] (contains? ladder-instructions name)) instr-names)
     ;; Must have at least one output coil
     (some (fn [name] (let [m (ladder-instructions name)]
                        (and m (:output m)))) instr-names)
     ;; Cannot have non-ladder instructions mixed in
     (not (some non-ladder-instructions instr-names)))))

(defn classify-instruction
  "Classify an instruction's role in ladder logic"
  [instr]
  (let [opcode (or (:name instr) (:opcode instr))
        m (ladder-instructions opcode)]
    (if m
      (assoc instr :ladder-type m)
      (assoc instr :ladder-type {:type :non-ladder}))))

(defn analyze-rung
  "Analyze a rung to determine if it can be rendered as ladder"
  [network-id instructions]
  (let [classified (mapv classify-instruction instructions)
        can-render? (can-render-as-ladder? classified)]
    {:network-id network-id
     :instructions classified
     :can-render-ladder? can-render?
     :instruction-count (count classified)}))

;;; ============================================================
;;; Ladder Symbol Generation
;;; ============================================================

(defn instruction-to-ladder-symbol
  "Convert an instruction to a ladder diagram symbol"
  [instr]
  (let [name (or (:name instr) (:opcode instr))
        args (or (:args instr) (:params instr))
        ladder-type (:ladder-type instr)]
    (case (:type ladder-type)
      ;; Series contacts (top rail)
      :series-contact
      {:symbol "│" :label (str (first args)) :type :contact-no}
      
      :series-contact-not
      {:symbol "│/" :label (str (first args)) :type :contact-nc}
      
      ;; Parallel contacts (separate branches)
      :parallel-contact
      {:symbol "╱" :label (str (first args)) :type :parallel-no}
      
      :parallel-contact-not
      {:symbol "╱╲" :label (str (first args)) :type :parallel-nc}
      
      ;; Edge detection
      :series-contact-pd
      {:symbol "│↑" :label (str (first args)) :type :contact-pd}
      
      :series-contact-nd
      {:symbol "│↓" :label (str (first args)) :type :contact-nd}
      
      ;; Comparisons
      :compare-eq
      {:symbol "=" :label (str (first args) " = " (second args)) :type :compare}
      
      :compare-ne
      {:symbol "≠" :label (str (first args) " ≠ " (second args)) :type :compare}
      
      :compare-gt
      {:symbol ">" :label (str (first args) " > " (second args)) :type :compare}
      
      :compare-lt
      {:symbol "<" :label (str (first args) " < " (second args)) :type :compare}
      
      :compare-ge
      {:symbol "≥" :label (str (first args) " ≥ " (second args)) :type :compare}
      
      :compare-le
      {:symbol "≤" :label (str (first args) " ≤ " (second args)) :type :compare}
      
      ;; Coils (right side)
      :coil
      {:symbol "( )" :label (str (first args)) :type :coil}
      
      :set-coil
      {:symbol "( S)" :label (str (first args)) :type :set-coil}
      
      :reset-coil
      {:symbol "( R)" :label (str (first args)) :type :reset-coil}
      
      :pulse-coil
      {:symbol "( P)" :label (str (first args)) :type :pulse-coil}
      
      ;; Stack operations
      :stack-and
      {:symbol "&" :label "ANDSTR" :type :stack-and}
      
      :stack-or
      {:symbol "|" :label "ORSTR" :type :stack-or}
      
      ;; Default
      {:symbol "?" :label name :type :unknown})))

;;; ============================================================
;;; SVG Rendering
;;; ============================================================

(defn svg-contact
  "Generate SVG for a contact (input)"
  [x y label negated?]
  (let [w 40 h 30
        x-end (+ x w)]
    [:g
     ;; Vertical lines (rails)
     [:line {:x1 x :y1 y :x2 x :y2 (+ y h) :stroke "black" :stroke-width 1}]
     [:line {:x1 x-end :y1 y :x2 x-end :y2 (+ y h) :stroke "black" :stroke-width 1}]
     
     ;; Contact box
     [:rect {:x (+ x 5) :y (+ y 5) :width 30 :height 20 :fill "white" :stroke "black" :stroke-width 1}]
     
     ;; Negation slash if needed
     (when negated?
       [:line {:x1 (+ x 10) :y1 (+ y 10) :x2 (+ x 25) :y2 (+ y 20) :stroke "black" :stroke-width 1}])
     
     ;; Label
     [:text {:x (+ x 20) :y (+ y 18) :text-anchor "middle" :font-size 12 :font-family "monospace"}
      label]]))

(defn svg-coil
  "Generate SVG for a coil (output)"
  [x y label coil-type]
  (let [w 40 h 30
        x-end (+ x w)]
    [:g
     ;; Vertical lines (rails)
     [:line {:x1 x :y1 y :x2 x :y2 (+ y h) :stroke "black" :stroke-width 1}]
     [:line {:x1 x-end :y1 y :x2 x-end :y2 (+ y h) :stroke "black" :stroke-width 1}]
     
     ;; Coil circle or modified circle
     [:circle {:cx (+ x 20) :cy (+ y 15) :r 12 :fill "white" :stroke "black" :stroke-width 1}]
     
     ;; Coil modifiers
     (case coil-type
       :set-coil [:line {:x1 (+ x 14) :y1 (+ y 12) :x2 (+ x 26) :y2 (+ y 18) :stroke "black" :stroke-width 1}]
       :reset-coil [:line {:x1 (+ x 14) :y1 (+ y 18) :x2 (+ x 26) :y2 (+ y 12) :stroke "black" :stroke-width 1}]
       :pulse-coil [:text {:x (+ x 20) :y (+ y 20) :text-anchor "middle" :font-size 10 :font-family "monospace"} "P"]
       :unknown [:text {:x (+ x 20) :y (+ y 20) :text-anchor "middle" :font-size 10 :font-family "monospace"} "?"]
       nil)
     
     ;; Label
     [:text {:x (+ x 20) :y (+ y 35) :text-anchor "middle" :font-size 11 :font-family "monospace"
             :font-weight "bold"}
      label]]))

(defn render-rung-as-svg
  "Render a single rung as SVG ladder diagram"
  [rung-num instructions]
  (let [inputs (filter #(contains? ladder-instructions (:name %)) instructions)
        outputs (filter #(let [m (ladder-instructions (:name %))]
                          (and m (:output m))) instructions)
        y-pos (* rung-num 60)]
    [:g {:id (str "rung-" rung-num)}
     ;; Top rail
     [:line {:x1 10 :y1 y-pos :x2 450 :y2 y-pos :stroke "black" :stroke-width 2}]
     
     ;; Render inputs (left side)
     (into [:g]
       (map-indexed (fn [i instr]
                     (svg-contact (+ 20 (* i 45)) (+ y-pos 5) 
                                 (str (first (:args instr))) 
                                 (str/includes? (:name instr) "N")))
                   inputs))
     
     ;; Render outputs (right side)
     (into [:g]
       (map-indexed (fn [i instr]
                     (let [coil-type (case (:name instr)
                                      "SET" :set-coil
                                      "RST" :reset-coil
                                      "PD" :pulse-coil
                                      nil)]
                       (svg-coil 360 (+ y-pos 5) 
                                (str (first (:args instr)))
                                coil-type)))
                   outputs))
     
     ;; Bottom rail
     [:line {:x1 10 :y1 (+ y-pos 40) :x2 450 :y2 (+ y-pos 40) :stroke "black" :stroke-width 2}]
     
     ;; Rung number
     [:text {:x -20 :y (+ y-pos 25) :text-anchor "end" :font-size 12 :font-family "monospace"}
      (str rung-num)]]))

(defn render-il-rung-as-svg
  "Render a rung that can't be converted to ladder as IL text"
  [rung-num instructions]
  (let [y-pos (* rung-num 60)
        il-text (str/join "\n" (map (fn [instr]
                                     (str (:name instr) " " 
                                          (str/join " " (:args instr))))
                                   instructions))]
    [:g {:id (str "rung-il-" rung-num)}
     ;; Background rect for IL text
     [:rect {:x 10 :y y-pos :width 440 :height 40 :fill "#fffacd" :stroke "orange" :stroke-width 1}]
     
     ;; IL text
     [:text {:x 20 :y (+ y-pos 20) :font-size 11 :font-family "monospace"}
      (str/join "\n" (take 2 (str/split il-text #"\n")))]
     
     ;; Rung number
     [:text {:x -20 :y (+ y-pos 25) :text-anchor "end" :font-size 12 :font-family "monospace"}
      (str rung-num)]]))

(defn render-network-as-svg
  "Render a complete network with multiple rungs"
  [network-id rungs]
  (let [height (* (count rungs) 60)]
    [:svg {:viewBox (str "0 0 500 " (+ height 40))
           :xmlns "http://www.w3.org/2000/svg"
           :style "border: 1px solid #ccc;"}
     ;; Title
     [:text {:x 250 :y 20 :text-anchor "middle" :font-size 14 :font-weight "bold" :font-family "monospace"}
      (str "Network " network-id)]
     
     ;; Rungs
     (into [:g {:transform "translate(0, 30)"}]
       (map-indexed (fn [i rung]
                     (if (:can-render-ladder? rung)
                       (render-rung-as-svg i (:instructions rung))
                       (render-il-rung-as-svg i (:instructions rung))))
                   rungs))]))

;;; ============================================================
;;; Hiccup to SVG Conversion
;;; ============================================================

(defn escape-attr [s]
  "Escape XML attribute values"
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "\"" "&quot;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn render-attrs [attrs]
  "Render attributes map to XML string"
  (str/join " "
    (map (fn [[k v]]
           (let [key-name (name k)]
             (str key-name "=\"" (escape-attr v) "\"")))
         attrs)))

(defn hiccup-to-svg
  "Convert Hiccup vector to SVG XML string"
  [hiccup]
  (cond
    (string? hiccup) hiccup
    (number? hiccup) (str hiccup)
    (vector? hiccup)
    (let [[tag attrs & children] hiccup
          tag-name (name tag)
          is-self-closing? (contains? #{:line :circle :rect :ellipse :polygon :polyline :path :image} tag)
          attrs-map (if (map? attrs) attrs {})
          attrs-str (if (seq attrs-map) (str " " (render-attrs attrs-map)) "")
          child-strs (if (map? attrs)
                       (map hiccup-to-svg children)
                       (map hiccup-to-svg (cons attrs children)))]
      (if is-self-closing?
        (str "<" tag-name attrs-str " />")
        (str "<" tag-name attrs-str ">"
             (str/join "" child-strs)
             "</" tag-name ">")))
    :else (str hiccup)))

;;; ============================================================
;;; Public API
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
                [(analyze-rung (:number network) (:instructions network))]])
             networks)))))

(defn generate-simple-ladder-svg
  "Generate a simple ladder diagram SVG for basic rungs"
  [network-id instruction-count]
  (hiccup-to-svg
    [:svg {:viewBox "0 0 600 200" :xmlns "http://www.w3.org/2000/svg"
           :style "border: 2px solid #667eea; background: white; border-radius: 5px;"}
     [:defs
      [:style "text { font-family: monospace; } .rail { stroke: black; stroke-width: 2; } .contact { stroke: black; fill: white; stroke-width: 1; } .coil { stroke: black; fill: white; stroke-width: 1; } .label { font-size: 12px; fill: #333; }"]]

     ;; Title
     [:text {:x "300" :y "25" :text-anchor "middle" :font-size "16" :font-weight "bold" :fill "#333"}
      (str "Network " network-id " - Ladder Logic")]

     ;; Left rail
     [:line {:x1 "20" :y1 "50" :x2 "20" :y2 "150" :class "rail"}]

     ;; Right rail
     [:line {:x1 "580" :y1 "50" :x2 "580" :y2 "150" :class "rail"}]

     ;; Top power bus
     [:line {:x1 "20" :y1 "50" :x2 "580" :y2 "50" :class "rail"}]

     ;; Bottom power bus
     [:line {:x1 "20" :y1 "150" :x2 "580" :y2 "150" :class "rail"}]

     ;; Sample rung (contact to coil)
     [:g
      ;; Contact box
      [:rect {:x "100" :y "85" :width "60" :height "30" :class "contact"}]
      [:text {:x "130" :y "105" :text-anchor "middle" :font-size "11" :fill "#333"} "Contact"]

      ;; Connection line
      [:line {:x1 "160" :y1 "100" :x2 "380" :y2 "100" :stroke "black" :stroke-width "1"}]

      ;; Coil circle
      [:circle {:cx "420" :cy "100" :r "20" :class "coil"}]
      [:text {:x "420" :y "107" :text-anchor "middle" :font-size "11" :fill "#333"} "Output"]]

     ;; Instructions count
     [:text {:x "300" :y "180" :text-anchor "middle" :font-size "11" :fill "#666"}
      (str instruction-count " instruction" (if (> instruction-count 1) "s" ""))]]))

(defn render-network
  "Render a single network as SVG ladder diagram"
  [network-id analyzed-rungs]
  (let [rungs (filter #(= (:network-id %) network-id) analyzed-rungs)]
    (if (seq rungs)
      (let [rung (first rungs)]
        (generate-simple-ladder-svg network-id (:instruction-count rung)))
      nil)))

(defn render-program-summary
  "Generate a summary of which rungs can/cannot be rendered as ladder"
  [analyzed-networks]
  (let [rungs (mapcat val analyzed-networks)]
    {:total-rungs (count rungs)
     :ladder-rungs (count (filter :can-render-ladder? rungs))
     :il-rungs (count (filter (complement :can-render-ladder?) rungs))
     :percentage (let [l (count (filter :can-render-ladder? rungs))]
                   (if (zero? (count rungs))
                     0
                     (int (* 100 (/ l (count rungs))))))}))
