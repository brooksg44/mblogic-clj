#!/bin/bash

# MBLogic-CLJ Startup Script
# A Common Lisp-inspired PLC Compiler/Interpreter written in Clojure
# Supports IL (Instruction List) programs with web-based UI

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Determine script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

show_help() {
  cat << 'EOF'
MBLogic-CLJ - Industrial PLC Compiler/Interpreter

Usage: ./MBLogic-CLJ.sh [COMMAND] [OPTIONS]

COMMANDS:
  server          Start web server (default)
  test            Run test suite
  repl            Start interactive REPL

OPTIONS:
  --port PORT     Port for web server (default: 8080)
  --help          Show this help message

EXAMPLES:
  # Start server on default port 8080
  ./MBLogic-CLJ.sh server

  # Start server on port 3000
  ./MBLogic-CLJ.sh server --port 3000

  # Run tests
  ./MBLogic-CLJ.sh test

  # Start interactive REPL
  ./MBLogic-CLJ.sh repl

REQUIREMENTS:
  - Java 11 or later
  - Leiningen (for Clojure project management)

DOCUMENTATION:
  See UserGuide.md for complete documentation
  See README.md for architecture overview
EOF
}

check_requirements() {
  echo -e "${BLUE}Checking requirements...${NC}"

  # Check Java
  if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java not found. Please install Java 11 or later${NC}"
    exit 1
  fi
  JAVA_VERSION=$(java -version 2>&1 | head -1)
  echo -e "${GREEN}✓ Java found: $JAVA_VERSION${NC}"

  # Check Leiningen
  if ! command -v lein &> /dev/null; then
    echo -e "${RED}✗ Leiningen not found. Please install Leiningen${NC}"
    echo "  Visit: https://leiningen.org/"
    exit 1
  fi
  echo -e "${GREEN}✓ Leiningen found$(lein version)${NC}"
}

start_server() {
  echo -e "${BLUE}Starting MBLogic-CLJ Web Server...${NC}"
  echo -e "${YELLOW}Port: $PORT${NC}"
  echo -e "${YELLOW}Web UI will be available at: http://localhost:$PORT${NC}"
  echo ""
  echo -e "${BLUE}Press Ctrl+C to stop the server${NC}"
  echo ""

  # Build and start server
  lein run -m mblogic-clj.core $PORT
}

run_tests() {
  echo -e "${BLUE}Running test suite...${NC}"
  echo ""
  lein test

  if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
  else
    echo ""
    echo -e "${RED}Some tests failed${NC}"
    exit 1
  fi
}

start_repl() {
  echo -e "${BLUE}Starting interactive REPL...${NC}"
  echo -e "${YELLOW}Type (quit) to exit${NC}"
  echo ""

  lein repl
}

# Default values
PORT=8080
COMMAND="server"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --port)
      PORT="$2"
      shift 2
      ;;
    --help|-h)
      show_help
      exit 0
      ;;
    test)
      COMMAND="test"
      shift
      ;;
    repl)
      COMMAND="repl"
      shift
      ;;
    server)
      COMMAND="server"
      shift
      ;;
    *)
      echo -e "${RED}Unknown option: $1${NC}"
      show_help
      exit 1
      ;;
  esac
done

# Main execution
case $COMMAND in
  server)
    check_requirements
    echo ""
    start_server
    ;;
  test)
    check_requirements
    echo ""
    run_tests
    ;;
  repl)
    check_requirements
    echo ""
    start_repl
    ;;
  *)
    show_help
    exit 1
    ;;
esac
