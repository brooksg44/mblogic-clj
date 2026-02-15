# MBLogic-CLJ Implementation Plan

## Overview
Migration of MBLogic-CL (Common Lisp) to MBLogic-CLJ (Clojure/ClojureScript). The final project must match exactly where we were at in development with mblogic-cl, including the browser interface that looks and functions identically.

**Original Source**: `/Users/gregorybrooks/Python/mblogic_all/mblogic_2011-04-16/mblogic/`
**Previous Port**: `/Users/gregorybrooks/common-lisp/mblogic-cl/`
**Target**: `/Users/gregorybrooks/Clojure/mblogic-clj/`
**Repository**: github.com/brooksg44/mblogic-clj

---

## Current State Analysis

### mblogic-cl (Common Lisp)
- **Core Engine**: ~4,600 lines of Lisp code across 9 files
  - Package definitions
  - Data structures (data-table.lisp)
  - Instruction definitions (instructions.lisp - 100+ instructions)
  - IL parser (parser.lisp)
  - Compiler (compiler.lisp - generates native code)
  - Interpreter (interpreter.lisp - scan-based execution)
  - Math library (math-lib.lisp)
  - Timer/counter operations (timer-counter.lisp)
  - Table operations (table-ops.lisp)

- **Web Layer**: ~81k lines of additional code
  - Web server (server.lisp - 13k, Hunchentoot-based)
  - Ladder diagram rendering (ladder-render.lisp - 56k, **CRITICAL**)
  - JSON API (json-api.lisp - 11k)
  - Static web interface (HTML/CSS/JavaScript)

- **Test Suite**: Multiple test files
  - Data table tests
  - Parser tests
  - Compiler tests
  - Interpreter tests
  - Ladder visualization tests

### Architecture Overview
```
IL Program Text
    ↓ (Parser)
Parsed Instructions
    ↓ (Compiler)
Clojure/Native Code
    ↓ (Interpreter)
PLC Execution with Data Tables
    ↓ (Ladder Renderer)
SVG/JSON Ladder Diagrams
    ↓ (Web Server)
Browser Interface
```

---

## Phase 1: Project Setup

### Phase 1.1: Create Leiningen Project Structure
- Initialize `project.clj` with dependencies
- Create directory structure:
  ```
  mblogic-clj/
  ├── project.clj
  ├── README.md
  ├── ImplementationPlan.md
  ├── src/
  │   └── mblogic_clj/
  │       ├── core.clj
  │       ├── data_table.clj
  │       ├── instructions.clj
  │       ├── parser.clj
  │       ├── compiler.clj
  │       ├── interpreter.clj
  │       ├── math_lib.clj
  │       ├── timer_counter.clj
  │       ├── table_ops.clj
  │       └── web/
  │           ├── server.clj
  │           ├── json_api.clj
  │           └── ladder_render.clj
  ├── src-cljs/
  │   └── mblogic_clj/
  │       └── ui.cljs
  ├── resources/
  │   ├── index.html
  │   ├── laddertest.xhtml
  │   ├── css/
  │   │   └── ladder.css
  │   └── js/
  │       └── (ClojureScript compiled output)
  ├── test/
  │   └── mblogic_clj/
  │       ├── data_table_test.clj
  │       ├── parser_test.clj
  │       ├── compiler_test.clj
  │       ├── interpreter_test.clj
  │       └── integration_test.clj
  └── .gitignore
  ```

### Phase 1.2: Dependencies Configuration
**Clojure Dependencies:**
- `clojure` (1.11+)
- `ring/ring-core` - Web server
- `ring/ring-jetty-adapter` - HTTP server
- `compojure` - Routing
- `cheshire` - JSON encoding/decoding
- `clojure.data/json` - Alternative JSON
- `com.clojure/tools.logging` - Logging
- `clj-commons/clj-yaml` - Configuration (optional)

**ClojureScript Dependencies:**
- `org.clojure/clojurescript`
- `shadow-cljs` - ClojureScript compiler

**Development Dependencies:**
- `clojure/clojure.test` - Testing framework
- `midje` - Additional testing (optional)

### Phase 1.3: Build Configuration
- Set up `shadow-cljs.edn` for ClojureScript compilation
- Create dev build target (unoptimized, source maps)
- Create prod build target (optimized)
- Set up `tools.build` for build automation (optional, or use Leiningen)

### Phase 1.4: Copy Static Assets
- Copy HTML files from mblogic-cl `static/` to `resources/`
- Copy CSS files
- Keep JavaScript files for reference (to be ported to ClojureScript)
- Copy test IL program (plcprog.txt)

### Phase 1.5: Git Setup
- Initialize git repository (already done)
- Create `.gitignore` for Clojure/ClojureScript build artifacts
- Initial commit with project structure

---

## Phase 2: Core Engine (Clojure)

### Phase 2.1: Data Structures (data_table.clj)
**Migrate from**: `src/data-table.lisp`

**Scope**:
- Address validation functions (bool, word, float, string address types)
- Data table creation and initialization
- Memory space setup (X, Y, C, SC, T, CT, DS, DD, DH, DF, TXT)
- Getter/setter functions for all data types
- Address parsing and generation

**Key Data Structures**:
- `:bool-table` - Map for X, Y, C, SC, T, CT addresses
- `:word-table` - Map for DS, DD, DH addresses
- `:float-table` - Map for DF addresses
- `:string-table` - Map for TXT addresses
- Address prefix/index parsing

### Phase 2.2: Instructions (instructions.clj)
**Migrate from**: `src/instructions.lisp`

**Scope**: Define 100+ IL instructions with:
- Instruction metadata (name, arity, behavior)
- Instruction categories (logic, output, comparisons, timers, counters, math, etc.)
- Validation rules
- Instruction registry/lookup

**Categories to implement**:
- Boolean logic (STR, STRN, AND, ANDN, OR, ORN, ANDSTR, ORSTR)
- Outputs (OUT, SET, RST, PD)
- Comparisons (STRE, STRGT, STRLT, STRGE, STRLE, STRNE, ANDE, ANDNE, etc.)
- Edges (STRPD, STRND, ANDPD, ANDND, ORPD, ORND)
- Timers (TMR, TMRA, TMROFF)
- Counters (CNTU, CNTD, UDC)
- Data Movement (COPY, CPYBLK, FILL, PACK, UNPACK)
- Math (MATHDEC, MATHHEX, SUM)
- Search (FINDEQ, FINDNE, FINDGT, FINDLT, FINDGE, FINDLE, etc.)
- Control (CALL, RT, RTC, END, ENDC, FOR, NEXT, NETWORK, SBR)

### Phase 2.3: Parser (parser.clj)
**Migrate from**: `src/parser.lisp`

**Scope**:
- Tokenize IL source code
- Parse networks and instructions
- Parse subroutines
- Handle comments and whitespace
- Generate instruction objects
- Error reporting and line number tracking

**Key Functions**:
- `tokenize-line` - Convert IL line to tokens
- `parse-instruction-line` - Create instruction object
- `parse-network` - Extract network-level instructions
- `parse-subroutine` - Extract subroutine definitions
- `parse-il-string` - Main entry point (from string)
- `parse-il-file` - Main entry point (from file)

### Phase 2.4: Math Library (math_lib.clj)
**Migrate from**: `src/math-lib.lisp`

**Scope**:
- MATHDEC operations (BCD/decimal conversions)
- MATHHEX operations (hex/unsigned conversions)
- SUM/aggregate operations
- Math expression parsing
- Function library definitions

### Phase 2.5: Timer/Counter (timer_counter.clj)
**Migrate from**: `src/timer-counter.lisp`

**Scope**:
- Timer implementation (TMR, TMRA, TMROFF)
- Counter implementation (CNTU, CNTD, UDC)
- Timer state management
- Counter state management
- Timer/counter tick logic

### Phase 2.6: Table Operations (table_ops.clj)
**Migrate from**: `src/table-ops.lisp`

**Scope**:
- COPY operations (single value copy)
- CPYBLK operations (block copy)
- FILL operations (range fill)
- PACK/UNPACK operations (bit packing)
- Search operations (FINDEQ, FINDNE, FINDGT, FINDLT, etc.)
- SHFRG operations (shift register)

### Phase 2.7: Compiler (compiler.clj)
**Migrate from**: `src/compiler.lisp`

**Scope**:
- Convert parsed instructions to Clojure code
- Generate code for each instruction type
- Handle instruction sequences
- Generate network execution logic
- Subroutine compilation
- Program compilation (all networks and subroutines)

**Key Functions**:
- `compile-instruction` - Single instruction → Clojure form
- `compile-network` - Network → Clojure function
- `compile-subroutine` - Subroutine → Clojure function
- `compile-program` - Full program → Executable

### Phase 2.8: Interpreter (interpreter.clj)
**Migrate from**: `src/interpreter.lisp`

**Scope**:
- PLC interpreter state management
- Scan cycle execution
- Program execution loop
- Data table management
- Subroutine call stack
- Error handling and runtime exceptions
- Continuous vs. step execution modes

**Key Functions**:
- `make-plc-interpreter` - Create interpreter instance
- `run-scan` - Execute single scan cycle
- `run-continuous` - Run program continuously
- `step-scan` - Single-step execution
- `get-bool-value` / `set-bool-value`
- `get-word-value` / `set-word-value`
- `get-float-value` / `set-float-value`
- `get-string-value` / `set-string-value`

---

## Phase 3: Web Backend (Clojure)

### Phase 3.1: Web Server (web/server.clj)
**Migrate from**: `src/web/server.lisp`

**Scope**:
- Ring-based web server setup
- Static file serving (HTML, CSS, JS)
- Background thread for PLC execution
- Thread-safe interpreter access (using atoms/refs)
- Server lifecycle management
- Port configuration

**Key Functions**:
- `start-web-server` - Start server
- `stop-web-server` - Stop server
- `start-plc-thread` - Run PLC in background
- Static file handler middleware
- Request logging middleware

### Phase 3.2: JSON API (web/json_api.clj)
**Migrate from**: `src/web/json-api.lisp`

**Scope**:
- JSON serialization helpers
- API response generation
- Data conversion for JSON encoding
- API endpoint handlers

**Endpoints to implement**:
- GET `/api/ladder-json` - Ladder diagram as JSON
- GET `/api/program-state` - Current program state
- GET `/api/data` - Current data table values
- POST `/api/execute-scan` - Execute one PLC scan
- GET `/api/subroutines` - List available subroutines
- POST `/api/load-program` - Load new IL program
- GET `/api/status` - Server and PLC status

### Phase 3.3: Ladder Rendering (web/ladder_render.clj)
**Migrate from**: `src/web/ladder-render.lisp` (56k - **LARGEST COMPONENT**)

**Scope**:
- Parse compiled program to ladder structure
- Generate ladder diagram JSON representation
- Cell rendering (contacts, coils, comparisons)
- Rung structure generation
- Branch handling
- SVG/HTML generation
- Ladder layout calculation

**Key Functions**:
- `program-to-ladder-json` - Main conversion function
- `render-rung` - Single rung → ladder JSON
- `render-cell` - Individual cell → ladder JSON
- `cell-type-to-symbol` - Instruction → ladder symbol
- `layout-cells` - Position cells horizontally
- Position tracking and coordinate calculation

**This is the CRITICAL piece** - it must match the visual output exactly.

---

## Phase 4: Frontend (ClojureScript)

### Phase 4.1: UI Component (ui.cljs)
**Migrate from**: `static/js/*.js` files

**Scope**:
- DOM manipulation
- Event handling
- Dropdown for subroutine selection
- Run/stop controls
- Ladder display rendering
- Data inspector display
- Button handlers

**Key Functions**:
- `init-ui` - Initialize frontend
- `update-ladder-display` - Render ladder diagram
- `update-data-inspector` - Show PLC data
- `handle-run-click` - Start PLC
- `handle-stop-click` - Stop PLC
- `handle-subroutine-select` - Change subroutine
- `fetch-ladder-json` - Get diagram from server
- `fetch-program-state` - Get PLC state

### Phase 4.2: Styling
- Keep existing CSS (no changes needed)
- Ensure responsive design works with ClojureScript UI

### Phase 4.3: HTML Templates
- Keep existing HTML structure
- Ensure semantic HTML
- Hook ClojureScript event handlers

---

## Phase 5: Testing & Verification

### Phase 5.1: Unit Tests
- **data_table_test.clj** - Address handling, memory access
- **instructions_test.clj** - Instruction registry and validation
- **parser_test.clj** - IL parsing with sample programs
- **compiler_test.clj** - Instruction compilation
- **interpreter_test.clj** - Execution correctness
- **math_lib_test.clj** - Math operations
- **timer_counter_test.clj** - Timer/counter behavior

### Phase 5.2: Integration Tests
- Load sample IL program (plcprog.txt)
- Compile and execute
- Verify data table state after execution
- Compare with mblogic-cl baseline results

### Phase 5.3: Visual Regression Tests
- Generate ladder JSON for all test programs
- Compare with mblogic-cl output
- Visual comparison of rendered diagrams
- Ensure exact layout match

### Phase 5.4: Performance Benchmarks
- Compare execution speed (JVM vs native)
- Profile hot paths
- Optimize if necessary

### Phase 5.5: Browser Testing
- Test laddertest.xhtml in modern browsers
- Verify UI interactions
- Test ladder diagram rendering
- Test data inspector updates
- Test subroutine selection

---

## File Mapping Reference

| Common Lisp File | Clojure File | Purpose |
|------------------|--------------|---------|
| src/package.lisp | src/mblogic_clj/core.clj | Package/module defs |
| src/data-table.lisp | src/mblogic_clj/data_table.clj | Address spaces, memory |
| src/instructions.lisp | src/mblogic_clj/instructions.clj | Instruction definitions |
| src/parser.lisp | src/mblogic_clj/parser.clj | IL → Instructions |
| src/compiler.lisp | src/mblogic_clj/compiler.clj | Instructions → Clojure |
| src/interpreter.lisp | src/mblogic_clj/interpreter.clj | Scan-based execution |
| src/math-lib.lisp | src/mblogic_clj/math_lib.clj | Math operations |
| src/timer-counter.lisp | src/mblogic_clj/timer_counter.clj | TMR/CNT logic |
| src/table-ops.lisp | src/mblogic_clj/table_ops.clj | Data movement |
| src/web/ladder-render.lisp | src/mblogic_clj/web/ladder_render.clj | SVG/JSON generation |
| src/web/json-api.lisp | src/mblogic_clj/web/json_api.clj | API responses |
| src/web/server.lisp | src/mblogic_clj/web/server.clj | Web server (Ring) |

---

## Dependencies Mapping

| Common Lisp Lib | Purpose | Clojure Equivalent |
|-----------------|---------|-------------------|
| cl-ppcre | Regular expressions | clojure.string, re-matches |
| alexandria | Utilities | clojure.core + reducers |
| parse-number | Number parsing | parse-long, parse-double |
| hunchentoot | Web server | ring/ring-core |
| cl-json | JSON encoding | cheshire or jsonista |
| bordeaux-threads | Concurrency | clojure concurrency (atoms) |
| fiveam | Testing | clojure.test |

---

## Development Workflow

### Local Development
```bash
# Start development REPL with auto-compilation
lein repl

# In REPL:
(require 'mblogic-clj.core)
(require 'mblogic-clj.parser)
(require 'mblogic-clj.compiler)
(require 'mblogic-clj.interpreter)

# Develop and test incrementally
```

### Build Process
```bash
# Run all tests
lein test

# Build for production
lein uberjar

# Compile ClojureScript
lein shadow release

# Watch mode for development
lein shadow watch dev
```

### Running the Server
```bash
# Development
lein run

# Production (from uberjar)
java -jar target/mblogic-clj-0.1.0-standalone.jar
```

---

## Estimated Timeline

| Phase | Tasks | Estimated Time |
|-------|-------|-----------------|
| Phase 1 | Project setup, dependencies, structure | 2-3 hours |
| Phase 2 | Core engine (8 modules) | 40-50 hours |
| Phase 2.1-2.3 | Data + Instructions + Parser | 12-15 hours |
| Phase 2.4-2.6 | Math + Timer/Counter + TableOps | 15-18 hours |
| Phase 2.7-2.8 | Compiler + Interpreter | 15-20 hours |
| Phase 3 | Web backend | 15-20 hours |
| Phase 3.1-3.2 | Server + JSON API | 5-8 hours |
| Phase 3.3 | **Ladder rendering (CRITICAL)** | 10-15 hours |
| Phase 4 | ClojureScript frontend | 8-10 hours |
| Phase 5 | Testing and verification | 15-20 hours |
| **TOTAL** | Full migration | **80-110 hours** |

---

## Success Criteria

1. ✓ Project compiles and runs without errors
2. ✓ Web server starts and serves static files
3. ✓ IL programs parse correctly (identical to mblogic-cl)
4. ✓ Programs compile to executable code
5. ✓ Interpreter executes programs with identical results
6. ✓ Ladder diagrams render identically to mblogic-cl
7. ✓ Browser interface looks and functions the same
8. ✓ All unit tests pass
9. ✓ Integration tests match mblogic-cl baseline
10. ✓ Performance is acceptable (no major slowdowns)

---

## Known Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Ladder rendering is complex (56k) | High | Break into smaller pieces, test heavily |
| Exact behavioral match needed | High | Run side-by-side tests with sample programs |
| JVM startup time | Medium | Use faster JVM or GraalVM native image |
| ClojureScript compilation | Medium | Use shadow-cljs (reliable, battle-tested) |
| Concurrency differences | Low | Use clojure.core.async if needed |

---

## Next Steps

1. **Complete Phase 1** - Set up project structure
2. **Start Phase 2.1** - Migrate data-table.clj first (foundational)
3. **Proceed incrementally** - Each phase builds on previous
4. **Test early and often** - Compare with mblogic-cl baseline
5. **Document as you go** - Add comments explaining ported logic

---

**Note**: This plan prioritizes functional correctness and exact behavioral match over optimization. Performance tuning comes after verification of correctness.
