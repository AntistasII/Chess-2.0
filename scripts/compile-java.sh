#!/bin/sh
# Собирает Java-пакеты любой вложенности; тестовые классы имеют отдельный каталог.
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"

case "${1:-}" in
  "") CHESS_CLASSES=backend/out ;;
  --tests) CHESS_CLASSES=backend/test-out ;;
  *) echo "Использование: sh scripts/compile-java.sh [--tests]" >&2; exit 2 ;;
esac

mkdir -p "$CHESS_CLASSES"
find backend/src/main/java -name '*.java' -print > "$CHESS_CLASSES/sources.list"
if [ "${1:-}" = --tests ]; then
  find backend/src/test/java -name '*.java' -print >> "$CHESS_CLASSES/sources.list"
fi
"$CHESS_JAVAC" --release 21 -encoding UTF-8 -d "$CHESS_CLASSES" "@$CHESS_CLASSES/sources.list"
