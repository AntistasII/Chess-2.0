#!/bin/sh
set -eu
case "${1:-}" in
  "") exec sh "$(dirname "$0")/maven.sh" -q -DskipTests compile ;;
  --tests) exec sh "$(dirname "$0")/maven.sh" -q -DskipTests test-compile ;;
  *) echo "Использование: sh scripts/compile-java.sh [--tests]" >&2; exit 2 ;;
esac
