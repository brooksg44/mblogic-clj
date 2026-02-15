# MBLogic-CLJ

A Clojure/ClojureScript port of the MBLogic PLC compiler/interpreter system. This project migrates the Common Lisp implementation (`mblogic-cl`) to modern Clojure, maintaining exact behavioral and visual compatibility.

## Overview

MBLogic-CLJ is a software-based PLC (Programmable Logic Controller) that:
- Compiles Instruction List (IL) programs to executable code
- Executes PLC programs with scan-based cycling
- Provides a web-based ladder diagram visualization interface
- Maintains 100+ industrial automation instructions

## Project Status

**Phase 1: Project Setup** ✓ COMPLETE
- Project structure initialized
- Dependencies configured
- Static assets copied
- Placeholder namespaces created

**Phase 2: Core Engine** 🚀 IN PROGRESS
- Data table structures (next)
- Instruction definitions
- IL parser
- Compiler
- Interpreter

**Phase 3: Web Backend** ⏳ PENDING
- Web server setup
- JSON API endpoints
- Ladder diagram rendering

**Phase 4: Frontend** ⏳ PENDING
- ClojureScript UI
- Browser interface

**Phase 5: Testing** ⏳ PENDING
- Unit tests
- Integration tests
- Visual regression tests

## Quick Start

### Prerequisites
- Java 11+ (for JVM)
- Leiningen 2.9+
- Node.js (for ClojureScript development)

### Installation

```bash
# Clone the repository
git clone https://github.com/brooksg44/mblogic-clj.git
cd mblogic-clj

# Install dependencies
lein deps
```

### Development

```bash
# Start a development REPL
lein repl

# In the REPL, start the server
(require 'mblogic-clj.core)
(mblogic-clj.core/-main)

# Access the web interface
# http://localhost:8080/laddertest.xhtml
```

### Building

```bash
# Compile ClojureScript
lein shadow release

# Build uberjar
lein uberjar

# Run standalone
java -jar target/mblogic-clj-0.1.0-standalone.jar
```

### Testing

```bash
# Run all tests
lein test

# Run specific test suite
lein test mblogic-clj.parser-test

# Run with coverage
lein cloverage
```

## Architecture

### Core Components

1. **Data Table** (`data_table.clj`)
   - Address space management (X, Y, C, SC, T, CT, DS, DD, DH, DF, TXT)
   - Memory access functions
   - Address validation

2. **Instructions** (`instructions.clj`)
   - ~100+ IL instruction definitions
   - Instruction registry and lookup
   - Validation and categorization

3. **Parser** (`parser.clj`)
   - Tokenizes IL source code
   - Parses instructions, networks, and subroutines
   - Error handling and line tracking

4. **Compiler** (`compiler.clj`)
   - Converts parsed instructions to Clojure forms
   - Generates executable network functions
   - Optimizes for performance

5. **Interpreter** (`interpreter.clj`)
   - Executes compiled programs
   - Manages scan cycles
   - Handles subroutine calls and state

6. **Web Server** (`web/server.clj`)
   - Ring-based HTTP server
   - Static file serving
   - Background PLC execution thread

7. **Ladder Rendering** (`web/ladder_render.clj`)
   - Converts programs to ladder diagram JSON
   - Generates visual representation
   - **CRITICAL: Must match mblogic-cl output exactly**

## File Structure

```
mblogic-clj/
├── ImplementationPlan.md          # Detailed migration plan
├── README.md                       # This file
├── project.clj                    # Leiningen configuration
├── src/
│   └── mblogic_clj/
│       ├── core.clj               # Main entry point
│       ├── data_table.clj         # Address spaces
│       ├── instructions.clj       # Instruction definitions
│       ├── parser.clj             # IL parser
│       ├── compiler.clj           # Code generator
│       ├── interpreter.clj        # Execution engine
│       ├── math_lib.clj           # Math operations
│       ├── timer_counter.clj      # Timer/counter logic
│       ├── table_ops.clj          # Data movement
│       └── web/
│           ├── server.clj         # HTTP server
│           ├── json_api.clj       # API endpoints
│           └── ladder_render.clj  # Ladder diagrams
├── src-cljs/
│   └── mblogic_clj/
│       └── ui.cljs                # ClojureScript UI
├── test/
│   └── mblogic_clj/
│       ├── data_table_test.clj
│       ├── parser_test.clj
│       ├── compiler_test.clj
│       ├── interpreter_test.clj
│       └── integration_test.clj
└── resources/
    ├── index.html
    ├── laddertest.xhtml           # Main interface
    ├── laddermonitor.html
    ├── css/
    │   └── ladder.css
    ├── js/                        # ClojureScript output
    └── plcprog.txt                # Test IL program
```

## Development Workflow

### Working on Phase 2: Core Engine

Each phase builds on previous work. When implementing a module:

1. **Start with the Lisp source** - Understand the original implementation
2. **Port to Clojure** - Translate idiomatically, not literally
3. **Write tests** - Test against mblogic-cl baseline
4. **Document** - Add comments explaining the logic
5. **Move to next module** - Respect dependency order

### Dependency Order (Important!)

The modules have dependencies - they must be implemented in order:

1. `data_table.clj` - Foundation, used by everything
2. `instructions.clj` - Instruction metadata, used by parser
3. `parser.clj` - Produces instruction objects
4. `math_lib.clj` - Used by compiler at compile-time
5. `timer_counter.clj` - Used by compiler at compile-time
6. `table_ops.clj` - Used by compiler at compile-time
7. `compiler.clj` - Uses all libraries above
8. `interpreter.clj` - Uses compiled programs

### Testing Strategy

- **Unit tests** - Test individual functions against known inputs
- **Baseline comparison** - Load same program in mblogic-cl and mblogic-clj, compare results
- **Visual regression** - Generate ladder diagrams, visually compare
- **Integration tests** - End-to-end program execution

## Key Differences from Common Lisp

- JVM bytecode instead of native compilation
- Atoms/refs instead of global mutable state
- Ring middleware instead of Hunchentoot handlers
- Idiomatic Clojure concurrency patterns
- ClojureScript instead of raw JavaScript

## Performance Considerations

- JVM startup time (~2-3 seconds)
- Scan cycle time comparable to native Lisp
- Consider GraalVM native image for production
- Profile before optimizing

## Known Issues & TODO

### Phase 2 Tasks
- [ ] Implement data_table.clj
- [ ] Implement instructions.clj
- [ ] Implement parser.clj
- [ ] Implement compiler.clj
- [ ] Implement interpreter.clj
- [ ] Implement math_lib.clj
- [ ] Implement timer_counter.clj
- [ ] Implement table_ops.clj

### Phase 3 Tasks
- [ ] Implement web/server.clj
- [ ] Implement web/json_api.clj
- [ ] Implement web/ladder_render.clj (CRITICAL)

### Phase 4 Tasks
- [ ] Port JavaScript to ClojureScript
- [ ] Implement UI interactions
- [ ] Verify browser compatibility

### Phase 5 Tasks
- [ ] Complete unit test suite
- [ ] Complete integration tests
- [ ] Visual regression tests
- [ ] Performance benchmarks

## Migration Reference

For each module being ported, refer to:
- **ImplementationPlan.md** - Detailed requirements
- **mblogic-cl source files** - Original implementation
- **mblogic-cl tests** - Test cases and baselines

## Contributing

When implementing a module:
1. Follow the phase order in ImplementationPlan.md
2. Maintain behavioral compatibility with mblogic-cl
3. Write comprehensive tests
4. Update progress in commit messages
5. Document any deviations from the Lisp version

## License

GPL-3.0 (consistent with original MBLogic and mblogic-cl projects)

## Contact & Support

- **Repository**: https://github.com/brooksg44/mblogic-clj
- **Issues**: GitHub Issues for bug reports and feature requests
- **Author**: Gregory Brooks

## Related Projects

- **MBLogic (Original)**: Python-based industrial automation system
- **MBLogic-CL**: Common Lisp port (previous iteration)
- **mblogic-cl source**: `/Users/gregorybrooks/common-lisp/mblogic-cl/`

---

**Current Phase**: Phase 1 Complete, Phase 2 Beginning

**Next Step**: Implement `src/mblogic_clj/data_table.clj` (Phase 2.1)
