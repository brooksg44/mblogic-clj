(ns mblogic-clj.core
  "ClojureScript frontend for MBLogic PLC ladder diagram viewer.
   Provides UI for viewing and controlling PLC execution."
  (:require [clojure.string :as str]
            [goog.dom :as dom]
            [goog.net.XhrIo :as xhr]
            [goog.events :as events]
            [goog.json :as json]
            [mblogic-clj.server-comm :as server]
            [mblogic-clj.ladder-viewer :as ladder]
            [mblogic-clj.control-panel :as control]))

;;; ============================================================
;;; Application State
;;; ============================================================

(def app-state
  "Global application state"
  (atom {:current-subroutine "main"
         :ladder-data nil
         :interpreter-status nil
         :data-table nil
         :subroutines []
         :loading false
         :error nil}))

;;; ============================================================
;;; Initialization
;;; ============================================================

(defn init-page
  "Initialize the application on page load"
  []
  (try
    (.log js/console "Initializing MBLogic frontend...")

    ;; Load subroutines list
    (server/fetch-subroutines
      (fn [subs]
        (swap! app-state assoc :subroutines subs)
        (.log js/console (str "Loaded " (count subs) " subroutines"))))

    ;; Load initial ladder diagram
    (server/fetch-ladder "main"
      (fn [ladder-data]
        (swap! app-state assoc :ladder-data ladder-data)
        (ladder/render-ladder ladder-data)))

    ;; Load interpreter status
    (server/fetch-status
      (fn [status]
        (swap! app-state assoc :interpreter-status status)))

    ;; Initialize control panel
    (control/init-control-panel)

    (.log js/console "Frontend initialized successfully")

    (catch js/Object e
      (.error js/console "Initialization failed:" e))))

(defn ^:export main
  "Main entry point called when page loads"
  []
  (init-page))

;;; ============================================================
;;; Page Event Handlers
;;; ============================================================

(defn on-subroutine-change
  "Handle subroutine selection change"
  [evt]
  (let [select (.-target evt)
        subrname (.-value select)]
    (swap! app-state assoc :current-subroutine subrname)
    (.log js/console (str "Switching to subroutine: " subrname))
    (server/fetch-ladder subrname
      (fn [ladder-data]
        (swap! app-state assoc :ladder-data ladder-data)
        (ladder/render-ladder ladder-data)))))

(defn on-status-refresh
  "Handle status refresh button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Refreshing interpreter status...")
  (server/fetch-status
    (fn [status]
      (swap! app-state assoc :interpreter-status status)
      (control/update-status-display status))))

(defn on-data-table-refresh
  "Handle data table refresh button click"
  [evt]
  (.preventDefault evt)
  (.log js/console "Refreshing data table...")
  (server/fetch-data-table
    (fn [data-table]
      (swap! app-state assoc :data-table data-table)
      (control/update-data-table-display data-table))))

;;; ============================================================
;;; UI Setup
;;; ============================================================

(defn setup-event-listeners
  "Attach event listeners to UI elements"
  []
  (try
    ;; Subroutine selector
    (when-let [sbr-select (dom/getElement "subrSelect")]
      (events/listen sbr-select "change" on-subroutine-change))

    ;; Status refresh button
    (when-let [refresh-btn (dom/getElement "refreshStatusBtn")]
      (events/listen refresh-btn "click" on-status-refresh))

    ;; Data table refresh button
    (when-let [dt-refresh-btn (dom/getElement "refreshDataTableBtn")]
      (events/listen dt-refresh-btn "click" on-data-table-refresh))

    (.log js/console "Event listeners attached")

    (catch js/Object e
      (.error js/console "Failed to attach event listeners:" e))))

;;; ============================================================
;;; Status Polling
;;; ============================================================

(def status-poll-interval
  "Milliseconds between status polls"
  1000)

(defn start-status-polling
  "Start periodic status polling"
  []
  (try
    (.setInterval js/window
      (fn []
        (server/fetch-status
          (fn [status]
            (swap! app-state assoc :interpreter-status status)
            (control/update-status-display status))))
      status-poll-interval)
    (.log js/console "Started status polling")
    (catch js/Object e
      (.error js/console "Failed to start polling:" e))))

;;; ============================================================
;;; Document Ready Handler
;;; ============================================================

(defn on-document-ready
  "Handle document ready event"
  []
  (try
    (.log js/console "Document ready, initializing...")
    (setup-event-listeners)
    (init-page)
    (start-status-polling)

    (catch js/Object e
      (.error js/console "Document ready failed:" e))))

;; Initialize when DOM is ready
(if (= (.-readyState js/document) "loading")
  (events/listen js/document "DOMContentLoaded" on-document-ready)
  (on-document-ready))

;;; End of core.cljs
