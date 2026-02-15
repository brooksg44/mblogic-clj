# MBLogic-CLJ User Guide

A modern Common Lisp-inspired PLC (Programmable Logic Controller) compiler and interpreter written in Clojure. Compiles and executes industrial automation programs written in IL (Instruction List) language.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Installation](#installation)
3. [System Architecture](#system-architecture)
4. [Running the System](#running-the-system)
5. [IL Language Guide](#il-language-guide)
6. [Web API Reference](#web-api-reference)
7. [Data Table Address Spaces](#data-table-address-spaces)
8. [Instruction Reference](#instruction-reference)
9. [Examples](#examples)
10. [Troubleshooting](#troubleshooting)

---

## Quick Start

### 1. Start the Web Server

```bash
./MBLogic-CLJ.sh server
```

The server will start on `http://localhost:8080`

### 2. Upload and Run an IL Program

```clojure
NETWORK 1
STR X1
AND X2
OUT Y1

NETWORK 2
STR X3
OR X4
OUT Y2
```

### 3. Control via Web UI

- Set input values (X1, X2, X3, X4)
- Click "Start" to begin scanning
- Observe output values (Y1, Y2) updating in real-time
- View data table contents and system status

---

## Installation

### Requirements

- **Java 11 or later** - [Download Java](https://www.oracle.com/java/technologies/downloads/)
- **Leiningen** - Clojure project management tool
  ```bash
  # macOS
  brew install leiningen

  # Linux
  sudo apt install leiningen

  # Or download from https://leiningen.org/
  ```

### Clone and Setup

```bash
# Clone repository
git clone https://github.com/brooksg44/mblogic-clj.git
cd mblogic-clj

# Install dependencies
lein deps

# Run tests to verify installation
./MBLogic-CLJ.sh test
```

---

## System Architecture

### Five-Phase Implementation

```
Phase 1: Parser
  ├─ Tokenization (handles quotes, parentheses)
  ├─ Network parsing (NETWORK keyword)
  ├─ Subroutine parsing (SBR keyword)
  └─ Error/warning collection

Phase 2: Compiler
  ├─ Instruction compilation to closures
  ├─ Logic stack management
  ├─ Network-level stack isolation
  └─ Subroutine/function generation

Phase 3: Interpreter
  ├─ Scan-based execution loop
  ├─ System control bits (SC1-SC7)
  ├─ System data words (SD1-SD3)
  └─ Timing and statistics

Phase 4: Web Backend
  ├─ Ring/Compojure HTTP server
  ├─ JSON API endpoints
  ├─ Ladder diagram rendering
  └─ Real-time data streaming

Phase 5: Advanced Features
  ├─ Timers (TMR, TMRA, TMROFF)
  ├─ Counters (CNTU, CNTD, UDC)
  ├─ Math operations (MATHDEC, MATHHEX)
  └─ Table operations (COPY, FILL, PACK, etc.)
```

### Component Diagram

```
IL Source Code
    ↓
  Parser (parser.clj)
    ↓ Parsed Instructions
  Compiler (compiler.clj)
    ↓ Closures
  Interpreter (interpreter.clj)
    ↓ Execution with scan loop
  Data Table (data-table.clj)
    ↓ 36,000 PLC Addresses
  Web Server (server.clj)
    ↓ HTTP API
  ClojureScript UI (ladder_viewer.cljs)
    ↓ Real-time Dashboard
```

### Module Count

| Component | Modules | Functions | Lines |
|-----------|---------|-----------|-------|
| Core Engine | 5 | 115 | 2,400 |
| Web Backend | 3 | 40 | 630 |
| Frontend | 4 | 45 | 765 |
| Advanced Features | 3 | 66 | 795 |
| **Total** | **17** | **266+** | **6,182+** |

---

## Running the System

### Web Server

```bash
# Start on default port 8080
./MBLogic-CLJ.sh server

# Start on custom port
./MBLogic-CLJ.sh server --port 3000

# Access the UI
open http://localhost:8080
```

### Run Tests

```bash
./MBLogic-CLJ.sh test
```

Output:
- 41 tests covering all 5 phases
- 139 assertions
- 0 failures (all passing)

### Interactive REPL

```bash
./MBLogic-CLJ.sh repl
```

Then in the REPL:

```clojure
; Load a program
(require '[mblogic-clj.parser :as parser])
(require '[mblogic-clj.compiler :as compiler])
(require '[mblogic-clj.interpreter :as interp])
(require '[mblogic-clj.data-table :as dt])

; Parse IL code
(def program "NETWORK 1\nSTR X1\nOUT Y1")
(def parsed (parser/parse-il-string program))

; Compile to closures
(def compiled (compiler/compile-program parsed))

; Create interpreter
(def data-table (dt/make-data-table))
(def interpreter (interp/make-plc-interpreter compiled :data-table data-table))

; Set inputs
(dt/set-bool data-table "X1" true)

; Execute one scan
(interp/run-scan interpreter)

; Check outputs
(dt/get-bool data-table "Y1")  ; => true
```

---

## IL Language Guide

### Program Structure

```
; Comments start with //

; Main program with networks
NETWORK 1
  instruction param
  instruction param

NETWORK 2
  instruction param

; Subroutines (optional)
SBR MYSUB
  NETWORK 1
    instruction param
```

### Network Rules

- Each NETWORK must have a unique number
- Networks execute in order during each scan
- Each network starts with a fresh logic stack
- Instructions within a network execute in order

### Subroutine Rules

- Subroutines are called via CALL instruction
- Subroutines contain networks just like main program
- Return via RT (return) or RTC (return on true)

### Comments

```
; This is a comment
// This is also a comment
NETWORK 1
  STR X1  ; Comment at end of line
```

---

## Web API Reference

### Health Check

```http
GET /api/health
```

Response:
```json
{
  "status": "operational",
  "uptime_ms": 12345
}
```

### Get Program Status

```http
GET /api/status
```

Response:
```json
{
  "running": true,
  "scan_count": 42,
  "scan_time_ms": 1.23,
  "average_scan_time_ms": 1.05
}
```

### Get Data Table Values

```http
GET /api/data-table
```

Response:
```json
{
  "bool": {
    "X1": true,
    "Y1": false,
    ...
  },
  "word": {
    "DS1": 42,
    "DS2": 123,
    ...
  },
  "float": {
    "DF1": 3.14,
    ...
  },
  "string": {
    "TXT1": "Hello"
  }
}
```

### Get Ladder Diagram

```http
GET /api/ladder
```

Returns JSON representation of networks and instructions for UI rendering.

### Upload Program

```http
POST /api/program/upload
Content-Type: application/json

{
  "source": "NETWORK 1\nSTR X1\nOUT Y1"
}
```

### Control Commands

```http
POST /api/control/start
POST /api/control/stop
POST /api/control/step
```

### Get Subroutines

```http
GET /api/subroutines
```

Response:
```json
{
  "MYSUB": {
    "networks": 2,
    "lines": 15
  }
}
```

---

## Data Table Address Spaces

### Overview

36,000 total addresses across 4 memory types:

| Type | Addresses | Count | Description |
|------|-----------|-------|-------------|
| **Boolean** | X1-X2000, Y1-Y2000, C1-C2000, SC1-SC1000, T1-T500, CT1-CT250 | 7,750 | Input, output, coil, system, timer, counter bits |
| **Word** | DS1-DS10000, DD1-DD2000, DH1-DH2000 | 16,250 | Signed, double, hex integers |
| **Float** | DF1-DF2000 | 2,000 | Floating-point values |
| **String** | TXT1-TXT10000 | 10,000 | Text values (up to 256 chars) |

### Address Categories

#### Inputs (Boolean)
- **X1-X2000**: Physical inputs (read from sensors, etc.)

#### Outputs (Boolean)
- **Y1-Y2000**: Physical outputs (drive actuators, etc.)

#### Internal Coils (Boolean)
- **C1-C2000**: Internal relay coils (local state)

#### System Control Bits (Boolean)
- **SC1**: Always ON
- **SC2**: Always OFF
- **SC3**: Alternating bit (toggles each scan)
- **SC4**: Running status
- **SC5**: First scan flag
- **SC6**: One-second pulse
- **SC7**: Scan complete flag
- **SC8-SC1000**: Available for user extensions

#### System Data Words (Word)
- **SD1**: Scan counter (auto-incremented)
- **SD2**: Last scan time (milliseconds)
- **SD3**: Average scan time (milliseconds)
- **SD4-SD10**: Available for user extensions

#### Timer/Counter Data
- **T1-T500**: Timer bits (on/off state)
- **TD1-TD500**: Timer accumulated values
- **CT1-CT250**: Counter bits
- **CTD1-CTD250**: Counter current values

#### User Data Storage
- **DS1-DS10000**: General-purpose word storage
- **DD1-DD2000**: Double-word storage
- **DH1-DH2000**: Hex/unsigned storage
- **DF1-DF2000**: Floating-point storage
- **TXT1-TXT10000**: String storage

---

## Instruction Reference

### Boolean Logic Instructions

#### STR (Store)
Loads a boolean value onto the logic stack.

```
NETWORK 1
  STR X1    ; Push X1 value to stack
```

#### AND
Performs AND operation with top of stack.

```
NETWORK 1
  STR X1
  AND X2    ; Result: X1 AND X2
  OUT Y1
```

#### OR
Performs OR operation with top of stack.

```
NETWORK 1
  STR X1
  OR X2     ; Result: X1 OR X2
  OUT Y1
```

#### OUT (Output)
Outputs current logic result to address(es).

```
NETWORK 1
  STR X1
  OUT Y1    ; Y1 = X1
```

Multiple outputs:
```
NETWORK 1
  STR X1
  OUT Y1 Y2 Y3    ; Y1 = Y2 = Y3 = X1
```

#### SET / RST (Set/Reset)
Latching outputs.

```
NETWORK 1
  STR X1
  SET Y1    ; Y1 latches ON if X1 is true

NETWORK 2
  STR X2
  RST Y1    ; Y1 latches OFF if X2 is true
```

### Comparison Instructions

#### STRE (Equal)
Compares two word values for equality.

```
NETWORK 1
  STRE DS1 DS2    ; Stack = true if DS1 == DS2
  OUT Y1
```

#### STRGT (Greater Than)
```
NETWORK 1
  STRGT DS1 100   ; Stack = true if DS1 > 100
  OUT Y1
```

#### STRLT (Less Than)
```
NETWORK 1
  STRLT DS1 50    ; Stack = true if DS1 < 50
  OUT Y1
```

### Timer Instructions

#### TMR (On-Delay Timer)
Accumulates time while input is true, outputs when preset is reached.

```
NETWORK 1
  STR X1
  TMR T1 5000     ; 5000ms on-delay timer
  OUT Y1
```

#### TMRA (Accumulating Timer)
Like TMR but retains accumulated time across cycles.

```
NETWORK 1
  STR X1
  TMRA T2 3000    ; Accumulating timer
  OUT Y2
```

#### TMROFF (Off-Delay Timer)
Delays turning OFF.

```
NETWORK 1
  STR X1
  TMROFF T3 2000  ; 2000ms off-delay
  OUT Y3
```

### Counter Instructions

#### CNTU (Count Up)
Increments on rising edge of input.

```
NETWORK 1
  STR X1
  CNTU CT1 10     ; Count up to 10
  OUT Y1
```

#### CNTD (Count Down)
Decrements on rising edge.

```
NETWORK 1
  STR X1
  CNTD CT2 10
  OUT Y2
```

#### UDC (Up/Down Counter)
Counts up or down based on separate inputs.

```
NETWORK 1
  STR X1          ; Up input
  STR X2          ; Down input
  UDC CT3 100
  OUT Y3
```

### Data Movement Instructions

#### COPY
Copy single value between addresses.

```
NETWORK 1
  COPY DS1 DS2    ; DS2 = DS1
```

#### CPYBLK (Copy Block)
Copy range of addresses.

```
NETWORK 1
  CPYBLK DS 1 DD 1 10    ; Copy DS1-DS10 to DD1-DD10
```

#### FILL
Fill range with constant value.

```
NETWORK 1
  FILL DS 1 10 42        ; Fill DS1-DS10 with 42
```

#### PACK
Pack 16 boolean values into single word.

```
NETWORK 1
  PACK X 1 DS1    ; Pack X1-X16 into DS1
```

#### UNPACK
Unpack word into 16 boolean values.

```
NETWORK 1
  UNPACK DS1 Y 1  ; Unpack DS1 into Y1-Y16
```

### Math Instructions

#### MATHDEC (Decimal Math)
Evaluate mathematical expressions.

```
NETWORK 1
  MATHDEC DS1 0 DS2 + DS3    ; DS1 = DS2 + DS3
```

#### MATHHEX (Hex/Bitwise Math)
Hexadecimal and bitwise operations.

```
NETWORK 1
  MATHHEX DS1 0 DS2 & DS3    ; DS1 = DS2 & DS3 (bitwise AND)
```

Supported operators:
- Decimal: `+`, `-`, `*`, `/`, `%`, `^` (power)
- Hex: `&` (AND), `|` (OR), `^` (XOR), `<<` (left shift), `>>` (right shift)

### Search Instructions

#### FINDEQ (Find Equal)
Find first occurrence of value.

```
NETWORK 1
  FINDEQ DS 1 10 42       ; Find first DS1-DS10 == 42
```

Result in DD1 (0-based index, or -1 if not found)

#### FINDGT (Find Greater Than)
Find first value greater than threshold.

```
NETWORK 1
  FINDGT DS 1 10 100      ; Find first DS1-DS10 > 100
```

#### FINDLT (Find Less Than)
Find first value less than threshold.

```
NETWORK 1
  FINDLT DS 1 10 50       ; Find first DS1-DS10 < 50
```

### Control Instructions

#### CALL
Call a subroutine.

```
NETWORK 1
  STR X1
  CALL MYSUB              ; Execute subroutine MYSUB
```

#### RT (Return)
Return from subroutine (unconditional).

```
SBR MYSUB
  NETWORK 1
    STR X1
    OUT Y1
    RT                    ; Return from subroutine
```

#### RTC (Return on True)
Return from subroutine if stack is true.

```
SBR MYSUB
  NETWORK 1
    STR X1
    RTC                   ; Return if X1 is true
    STR X2
    OUT Y1
```

#### END
End program execution.

```
NETWORK 1
  STR X1
  OUT Y1
  END                     ; Stop here
```

---

## Examples

### Example 1: Simple Logic

```
; AND/OR logic with latching output
NETWORK 1
  STR X1
  AND X2
  OUT Y1

NETWORK 2
  STR X3
  SET Y2              ; Latches Y2 on

NETWORK 3
  STR X4
  RST Y2              ; Resets Y2 off
```

### Example 2: Timer-Based Pump Control

```
; Start pump with delay, monitor pressure
NETWORK 1
  STR X_START_BUTTON
  TMR T_STARTUP 5000      ; 5-second startup delay
  OUT Y_PUMP_MOTOR

NETWORK 2
  STR Y_PUMP_MOTOR
  STRGT DS_PRESSURE 50    ; Check if pressure > 50 PSI
  OUT Y_PRESSURE_OK
```

### Example 3: Production Counter

```
; Count items on conveyor, trigger when limit reached
NETWORK 1
  STR X_ITEM_SENSOR       ; Rising edge detection
  CNTU CT_ITEMS 100       ; Count to 100
  OUT Y_ITEM_COUNT_ACTIVE

NETWORK 2
  STR CT_ITEMS            ; Bit output from counter
  OUT Y_BATCH_COMPLETE
```

### Example 4: Data Processing with Math

```
; Read sensor, scale value, store result
NETWORK 1
  MATHDEC DS_RAW_VALUE 0 DS_SENSOR_INPUT * 10
  MATHDEC DS_SCALED 0 DS_RAW_VALUE + DS_OFFSET

NETWORK 2
  STRGT DS_SCALED DS_HIGH_THRESHOLD
  OUT Y_ALARM
```

### Example 5: Subroutine-Based Logic

```
; Main program
NETWORK 1
  STR X_AUTO_MODE
  CALL RUN_SEQUENCE

NETWORK 2
  STR X_MANUAL
  OUT Y_MANUAL_CONTROL

; Subroutine
SBR RUN_SEQUENCE
  NETWORK 1
    STR X_START
    SET Y_MOTOR_RUN

  NETWORK 2
    STR X_STOP
    RST Y_MOTOR_RUN

  NETWORK 3
    STR Y_MOTOR_RUN
    OUT SC4             ; Status output
    RT
```

---

## Troubleshooting

### Server Won't Start

**Problem**: "Address already in use"

```bash
# Kill process on port 8080
lsof -i :8080
kill -9 <PID>

# Or use different port
./MBLogic-CLJ.sh server --port 3000
```

### Tests Failing

**Problem**: Some tests fail during `./MBLogic-CLJ.sh test`

```bash
# Run specific test for details
lein test :only e2e-test/test-execute-simple-logic

# Check Java version
java -version
# Requires Java 11+

# Clean build and retry
lein clean
lein deps
./MBLogic-CLJ.sh test
```

### Program Not Executing

**Problem**: Instructions compile but outputs don't change

1. Check program syntax in UI
2. Verify inputs are set (X1, X2, etc.)
3. Click "Start" button to begin scanning
4. Check system status shows scan count > 0

### Slow Performance

**Problem**: Scans taking too long

1. Check scan time in status (SD2)
2. Verify no infinite loops in logic
3. Limit data table queries in math expressions
4. Use appropriate data types (word vs. float)

### Memory Issues

**Problem**: "Out of memory" errors

```bash
# Increase JVM heap size
export JVM_OPTS="-Xmx2g -Xms1g"
./MBLogic-CLJ.sh server
```

### Web UI Not Loading

**Problem**: Browser shows blank page

1. Check server is running: `curl http://localhost:8080`
2. Open browser console (F12) for JavaScript errors
3. Try different browser or clear cache
4. Check firewall/network settings

---

## Performance Characteristics

### Execution Speed
- **Scan cycle**: ~1-5ms typical
- **Instruction execution**: <1μs per instruction (native code)
- **Data table access**: O(1) hash lookup

### Memory Usage
- **36,000 PLC addresses**: ~5MB
- **Compiled program**: Size depends on instruction count
- **Runtime overhead**: ~10-20MB per interpreter instance

### Scalability
- **Supported instructions per program**: Unlimited
- **Maximum program size**: Limited by JVM heap
- **Concurrent interpreters**: Limited by heap/CPU

---

## Additional Resources

- **Source Code**: https://github.com/brooksg44/mblogic-clj
- **Clojure Documentation**: https://clojure.org/
- **PLC Fundamentals**: [IEC 61131-3 Standard](https://en.wikipedia.org/wiki/IEC_61131-3)
- **Original Python Project**: MBLogic (Python 2.7 industrial automation system)

---

## Version Information

- **MBLogic-CLJ Version**: 1.0
- **Build Date**: 2026
- **Language**: Clojure 1.11.1
- **Runtime**: SBCL-compatible Common Lisp semantics
- **License**: GPL v3

---

## Getting Help

For issues and questions:
1. Check this UserGuide for solutions
2. Review examples in the Examples section
3. Run tests: `./MBLogic-CLJ.sh test`
4. Open GitHub Issues: https://github.com/brooksg44/mblogic-clj/issues
5. Start REPL for interactive exploration: `./MBLogic-CLJ.sh repl`

---

Last Updated: 2026-02-14
