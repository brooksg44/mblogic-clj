(ns mblogic-clj.data-table-test
  (:require [clojure.test :refer :all]
            [mblogic-clj.data-table :as dt]))

;;; ============================================================
;;; Test Fixtures
;;; ============================================================

(def test-data-table (dt/make-data-table))

(defn setup-fixtures [f]
  "Setup/teardown for each test - provides fresh data table"
  (f))

(use-fixtures :each setup-fixtures)

;;; ============================================================
;;; Data Table Creation Tests
;;; ============================================================

(deftest test-make-data-table
  (testing "Creating a new data table"
    (let [dt (dt/make-data-table)]
      (is (some? dt) "Data table should not be nil")
      (is (instance? mblogic_clj.data_table.DataTable dt)
          "Should return DataTable instance"))))

(deftest test-data-table-initialization
  (testing "Data table has correct number of addresses"
    (let [dt (dt/make-data-table)]
      ;; Boolean: X(2000) + Y(2000) + C(2000) + SC(1000) + T(500) + CT(250) = 7750
      (is (= 7750 (dt/count-bool-addresses dt))
          "Should have 7750 boolean addresses")
      ;; Word: XD(125) + YD(125) + XS(125) + YS(125) + DS(10000) + DD(2000) + DH(2000) + SD(1000) + TD(500) + CTD(250) = 16250
      (is (= 16250 (dt/count-word-addresses dt))
          "Should have 16250 word addresses")
      ;; Float: DF(2000) = 2000
      (is (= 2000 (dt/count-float-addresses dt))
          "Should have 2000 float addresses")
      ;; String: TXT(10000) = 10000
      (is (= 10000 (dt/count-string-addresses dt))
          "Should have 10000 string addresses"))))

(deftest test-total-address-count
  (testing "Total address count"
    (let [dt (dt/make-data-table)
          total (dt/total-address-count dt)]
      ;; 7750 + 16250 + 2000 + 10000 = 36000
      (is (= 36000 total)
          "Should have 36000 total addresses"))))

;;; ============================================================
;;; Address Generation Tests
;;; ============================================================

(deftest test-generate-addresses
  (testing "Address generation"
    (let [addrs (dt/generate-addresses "X" 1 5)]
      (is (= 5 (count addrs)) "Should generate 5 addresses")
      (is (= ["X1" "X2" "X3" "X4" "X5"] addrs)
          "Should generate correct address labels"))))

(deftest test-generate-all-boolean-addresses
  (testing "Generate all boolean addresses"
    (let [addrs (dt/generate-all-boolean-addresses)]
      (is (= 7750 (count addrs)) "Should generate 7750 boolean addresses")
      (is (some #(= % "X1") addrs) "Should contain X1")
      (is (some #(= % "X2000") addrs) "Should contain X2000")
      (is (some #(= % "Y1") addrs) "Should contain Y1")
      (is (some #(= % "C1") addrs) "Should contain C1")
      (is (some #(= % "SC1000") addrs) "Should contain SC1000")
      (is (some #(= % "T500") addrs) "Should contain T500")
      (is (some #(= % "CT250") addrs) "Should contain CT250"))))

;;; ============================================================
;;; Boolean Address Tests
;;; ============================================================

(deftest test-bool-addr-p
  (testing "Boolean address validation"
    (is (true? (dt/bool-addr-p "X1")) "X1 should be valid")
    (is (true? (dt/bool-addr-p "X2000")) "X2000 should be valid")
    (is (true? (dt/bool-addr-p "Y1")) "Y1 should be valid")
    (is (true? (dt/bool-addr-p "C1")) "C1 should be valid")
    (is (true? (dt/bool-addr-p "SC1000")) "SC1000 should be valid")
    (is (true? (dt/bool-addr-p "T500")) "T500 should be valid")
    (is (true? (dt/bool-addr-p "CT250")) "CT250 should be valid")
    (is (false? (dt/bool-addr-p "X0")) "X0 should be invalid (out of range)")
    (is (false? (dt/bool-addr-p "X2001")) "X2001 should be invalid (out of range)")
    (is (false? (dt/bool-addr-p "Z1")) "Z1 should be invalid (wrong prefix)")
    (is (false? (dt/bool-addr-p "DS1")) "DS1 should be invalid (not boolean)")))

(deftest test-get-set-bool
  (testing "Get and set boolean values"
    (let [dt (dt/make-data-table)]
      ;; Get initial value (should be false)
      (is (false? (dt/get-bool dt "X1")) "Initial value should be false")
      ;; Set to true
      (dt/set-bool dt "X1" true)
      (is (true? (dt/get-bool dt "X1")) "After setting to true, should be true")
      ;; Set back to false
      (dt/set-bool dt "X1" false)
      (is (false? (dt/get-bool dt "X1")) "After setting to false, should be false"))))

(deftest test-get-set-bool-multiple
  (testing "Set and get multiple boolean values"
    (let [dt (dt/make-data-table)]
      (dt/set-bool dt "X1" true)
      (dt/set-bool dt "Y1" true)
      (dt/set-bool dt "C1" false)
      (is (true? (dt/get-bool dt "X1")))
      (is (true? (dt/get-bool dt "Y1")))
      (is (false? (dt/get-bool dt "C1"))))))

;;; ============================================================
;;; Word Address Tests
;;; ============================================================

(deftest test-word-addr-p
  (testing "Word address validation"
    (is (true? (dt/word-addr-p "DS1")) "DS1 should be valid")
    (is (true? (dt/word-addr-p "DS10000")) "DS10000 should be valid")
    (is (true? (dt/word-addr-p "DD1")) "DD1 should be valid")
    (is (true? (dt/word-addr-p "DH2000")) "DH2000 should be valid")
    (is (true? (dt/word-addr-p "XD1")) "XD1 should be valid")
    (is (false? (dt/word-addr-p "DS0")) "DS0 should be invalid")
    (is (false? (dt/word-addr-p "DS10001")) "DS10001 should be invalid")
    (is (false? (dt/word-addr-p "X1")) "X1 should be invalid (not word)")))

(deftest test-get-set-word
  (testing "Get and set word values"
    (let [dt (dt/make-data-table)]
      ;; Get initial value (should be 0)
      (is (= 0 (dt/get-word dt "DS1")) "Initial value should be 0")
      ;; Set to positive number
      (dt/set-word dt "DS1" 100)
      (is (= 100 (dt/get-word dt "DS1")) "After setting to 100, should be 100")
      ;; Set to negative number
      (dt/set-word dt "DS1" -50)
      (is (= -50 (dt/get-word dt "DS1")) "After setting to -50, should be -50"))))

(deftest test-get-set-word-range
  (testing "Get and set word value ranges"
    (let [dt (dt/make-data-table)]
      ;; Set a range to same value
      (dt/set-word-range dt "DS" 1 5 42)
      ;; Verify each value in range
      (is (= 42 (dt/get-word dt "DS1")))
      (is (= 42 (dt/get-word dt "DS2")))
      (is (= 42 (dt/get-word dt "DS5")))
      ;; Verify values outside range are unchanged
      (is (= 0 (dt/get-word dt "DS6"))))))

;;; ============================================================
;;; Float Address Tests
;;; ============================================================

(deftest test-float-addr-p
  (testing "Float address validation"
    (is (true? (dt/float-addr-p "DF1")) "DF1 should be valid")
    (is (true? (dt/float-addr-p "DF2000")) "DF2000 should be valid")
    (is (false? (dt/float-addr-p "DF0")) "DF0 should be invalid")
    (is (false? (dt/float-addr-p "DF2001")) "DF2001 should be invalid")
    (is (false? (dt/float-addr-p "DS1")) "DS1 should be invalid (not float)")))

(deftest test-get-set-float
  (testing "Get and set float values"
    (let [dt (dt/make-data-table)]
      ;; Get initial value (should be 0.0)
      (is (= 0.0 (dt/get-float dt "DF1")) "Initial value should be 0.0")
      ;; Set to positive float
      (dt/set-float dt "DF1" 3.14159)
      (is (= 3.14159 (dt/get-float dt "DF1")) "After setting, should have correct value")
      ;; Set to negative float
      (dt/set-float dt "DF1" -2.71828)
      (is (= -2.71828 (dt/get-float dt "DF1")) "After setting negative, should have correct value"))))

;;; ============================================================
;;; String Address Tests
;;; ============================================================

(deftest test-string-addr-p
  (testing "String address validation"
    (is (true? (dt/string-addr-p "TXT1")) "TXT1 should be valid")
    (is (true? (dt/string-addr-p "TXT10000")) "TXT10000 should be valid")
    (is (false? (dt/string-addr-p "TXT0")) "TXT0 should be invalid")
    (is (false? (dt/string-addr-p "TXT10001")) "TXT10001 should be invalid")
    (is (false? (dt/string-addr-p "DS1")) "DS1 should be invalid (not string)")))

(deftest test-get-set-string
  (testing "Get and set string values"
    (let [dt (dt/make-data-table)]
      ;; Get initial value (should be empty string)
      (is (= "" (dt/get-string dt "TXT1")) "Initial value should be empty string")
      ;; Set to string
      (dt/set-string dt "TXT1" "Hello World")
      (is (= "Hello World" (dt/get-string dt "TXT1")) "After setting, should have correct value")
      ;; Set to different string
      (dt/set-string dt "TXT1" "Clojure PLC")
      (is (= "Clojure PLC" (dt/get-string dt "TXT1")) "After changing, should have new value"))))

;;; ============================================================
;;; Any Address Type Tests
;;; ============================================================

(deftest test-any-addr-p
  (testing "Generic address validation"
    (is (true? (dt/any-addr-p "X1")) "X1 should be valid")
    (is (true? (dt/any-addr-p "DS1")) "DS1 should be valid")
    (is (true? (dt/any-addr-p "DF1")) "DF1 should be valid")
    (is (true? (dt/any-addr-p "TXT1")) "TXT1 should be valid")
    (is (false? (dt/any-addr-p "Z1")) "Z1 should be invalid")))

(deftest test-get-set-value-generic
  (testing "Generic get/set value (auto-detect type)"
    (let [dt (dt/make-data-table)]
      ;; Boolean
      (dt/set-value dt "X1" true)
      (is (true? (dt/get-value dt "X1")) "Boolean value should be set/get")
      ;; Word
      (dt/set-value dt "DS1" 123)
      (is (= 123 (dt/get-value dt "DS1")) "Word value should be set/get")
      ;; Float
      (dt/set-value dt "DF1" 1.5)
      (is (= 1.5 (dt/get-value dt "DF1")) "Float value should be set/get")
      ;; String
      (dt/set-value dt "TXT1" "test")
      (is (= "test" (dt/get-value dt "TXT1")) "String value should be set/get"))))

;;; ============================================================
;;; Checked Access Tests (with validation)
;;; ============================================================

(deftest test-checked-access
  (testing "Access with validation"
    (let [dt (dt/make-data-table)]
      ;; Valid boolean address
      (is (false? (dt/get-bool-checked dt "X1"))
          "get-bool-checked should work for valid address")
      (dt/set-bool-checked dt "X1" true)
      (is (true? (dt/get-bool-checked dt "X1"))
          "set-bool-checked should work for valid address"))))

;;; ============================================================
;;; Query and Snapshot Tests
;;; ============================================================

(deftest test-snapshot
  (testing "Getting a snapshot of data table state"
    (let [dt (dt/make-data-table)]
      ;; Modify some values
      (dt/set-bool dt "X1" true)
      (dt/set-word dt "DS1" 42)
      (dt/set-float dt "DF1" 3.14)
      (dt/set-string dt "TXT1" "test")
      ;; Get snapshot
      (let [snap (dt/snapshot dt)]
        (is (map? snap) "Snapshot should be a map")
        (is (contains? snap :bool) "Should have :bool key")
        (is (contains? snap :word) "Should have :word key")
        (is (contains? snap :float) "Should have :float key")
        (is (contains? snap :string) "Should have :string key")
        ;; Verify values in snapshot
        (is (true? (get-in snap [:bool "X1"])))
        (is (= 42 (get-in snap [:word "DS1"])))
        (is (= 3.14 (get-in snap [:float "DF1"])))
        (is (= "test" (get-in snap [:string "TXT1"])))))))

;;; ============================================================
;;; Edge Cases and Error Handling
;;; ============================================================

(deftest test-invalid-addresses
  (testing "Accessing invalid addresses"
    (let [dt (dt/make-data-table)]
      ;; Getting non-existent address returns nil
      (is (nil? (dt/get-bool dt "INVALID")) "Invalid address should return nil")
      ;; Invalid address validation
      (is (false? (dt/bool-addr-p "INVALID")) "Invalid should fail validation")
      (is (false? (dt/any-addr-p "INVALID")) "Invalid should fail any validation"))))

(deftest test-address-boundaries
  (testing "Test boundary addresses"
    (let [dt (dt/make-data-table)]
      ;; Min and max boolean addresses
      (is (true? (dt/bool-addr-p "X1")) "Min X should be valid")
      (is (true? (dt/bool-addr-p "X2000")) "Max X should be valid")
      ;; Min and max word addresses
      (is (true? (dt/word-addr-p "DS1")) "Min DS should be valid")
      (is (true? (dt/word-addr-p "DS10000")) "Max DS should be valid"))))

;;; ============================================================
;;; Immutability and Thread Safety Tests
;;; ============================================================

(deftest test-independent-instances
  (testing "Multiple data table instances are independent"
    (let [dt1 (dt/make-data-table)
          dt2 (dt/make-data-table)]
      ;; Modify dt1
      (dt/set-bool dt1 "X1" true)
      (dt/set-word dt1 "DS1" 42)
      ;; dt2 should not be affected
      (is (false? (dt/get-bool dt2 "X1")) "dt2 X1 should still be false")
      (is (= 0 (dt/get-word dt2 "DS1")) "dt2 DS1 should still be 0"))))

(deftest test-data-persistence
  (testing "Changes persist across multiple accesses"
    (let [dt (dt/make-data-table)]
      ;; Set value
      (dt/set-bool dt "X1" true)
      ;; Access multiple times
      (is (true? (dt/get-bool dt "X1")))
      (is (true? (dt/get-bool dt "X1")))
      (is (true? (dt/get-bool dt "X1"))))))

;;; ============================================================
;;; Performance-related Tests
;;; ============================================================

(deftest test-large-address-access
  (testing "Access to high-numbered addresses"
    (let [dt (dt/make-data-table)]
      ;; Boolean
      (dt/set-bool dt "X2000" true)
      (is (true? (dt/get-bool dt "X2000")))
      ;; Word
      (dt/set-word dt "DS10000" 9999)
      (is (= 9999 (dt/get-word dt "DS10000")))
      ;; Float
      (dt/set-float dt "DF2000" 9.99)
      (is (= 9.99 (dt/get-float dt "DF2000")))
      ;; String
      (dt/set-string dt "TXT10000" "last")
      (is (= "last" (dt/get-string dt "TXT10000"))))))
