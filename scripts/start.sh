#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
if [ ! -f backend/out/chess/server/Server.class ] || [ ! -f frontend/dist/index.html ]; then
  sh scripts/build.sh
fi
exec "$CHESS_JAVA" -cp backend/out chess.server.Server "${1:-8080}"
