#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT/frontend"
npm run build
cd "$PROJECT_ROOT"
exec sh scripts/maven.sh -DskipTests package
