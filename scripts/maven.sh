#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
JAVA_HOME=$(dirname "$(dirname "$CHESS_JAVA")")
export JAVA_HOME
MAVEN_USER_HOME="$PROJECT_ROOT/.tools/maven-user"
export MAVEN_USER_HOME
exec sh ./mvnw -B -ntp "-Dmaven.repo.local=$PROJECT_ROOT/.tools/m2" "$@"
