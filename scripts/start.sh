#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
if [ ! -f backend/target/card-chess.jar ] || [ ! -f frontend/dist/index.html ]; then
  sh scripts/build.sh
fi
exec "$CHESS_JAVA" -jar backend/target/card-chess.jar "$@"
