#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
sh scripts/compile-java.sh
cd frontend
npm run build
