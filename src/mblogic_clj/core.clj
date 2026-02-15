(ns mblogic-clj.core
  "Core namespace for MBLogic-CLJ PLC compiler/interpreter.
   Ported from MBLogic-CL Common Lisp implementation."
  (:require [clojure.tools.logging :as log]
            [mblogic-clj.web.server :as server]
            [mblogic-clj.parser :as parser]
            [mblogic-clj.compiler :as compiler]
            [mblogic-clj.interpreter :as interpreter])
  (:gen-class))

;;; ============================================================
;;; Initialization and Configuration
;;; ============================================================

(def default-config
  "Default server configuration"
  {:port 8080
   :host "localhost"
   :static-dir "resources"
   :program-file "resources/plcprog.txt"})

;;; ============================================================
;;; Main Entry Point
;;; ============================================================

(defn -main
  "Main entry point for the MBLogic-CLJ server.
   Starts the web server and PLC interpreter."
  [& args]
  (log/info "Starting MBLogic-CLJ PLC Server...")

  (let [config (merge default-config
                      (when (seq args)
                        {:port (parse-long (first args))}))]
    (log/info "Configuration:" config)

    ;; Start the web server
    (server/start-web-server config)

    (log/info "MBLogic-CLJ running at http://{}:{}"
              (:host config) (:port config))

    ;; Keep the server running
    (Thread/sleep Long/MAX_VALUE)))

;;; ============================================================
;;; Development Helpers
;;; ============================================================

(defn load-program
  "Load and compile an IL program from a file.
   Returns: {:parsed parsed-program :compiled compiled-program}"
  [program-file]
  (log/info "Loading program from" program-file)
  (let [source (slurp program-file)
        parsed (parser/parse-il-string source)
        compiled (compiler/compile-program parsed)]
    {:parsed parsed
     :compiled compiled
     :source source}))

(defn run-program
  "Load, compile, and run an IL program.
   Returns: interpreter instance"
  [program-file]
  (let [{:keys [compiled]} (load-program program-file)
        interp (interpreter/make-plc-interpreter :program compiled)]
    (interpreter/run-continuous interp :target-scan-time 10)
    interp))

(defn load-and-test
  "Load a program and run a single scan for testing."
  [program-file]
  (let [{:keys [compiled]} (load-program program-file)
        interp (interpreter/make-plc-interpreter :program compiled)]
    (interpreter/run-scan interp)
    {:interpreter interp
     :data-tables (interpreter/data-tables interp)}))
