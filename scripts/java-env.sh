#!/bin/sh
# Prefer a system JDK, then the project-local JDK.
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  CHESS_JAVA="$JAVA_HOME/bin/java"; CHESS_JAVAC="$JAVA_HOME/bin/javac"
else
  CHESS_JAVA=""; CHESS_JAVAC=""
  for candidate in "$PROJECT_ROOT"/.tools/jdk*/Contents/Home "$PROJECT_ROOT"/.tools/jdk*; do
    if [ -x "$candidate/bin/javac" ]; then CHESS_JAVA="$candidate/bin/java"; CHESS_JAVAC="$candidate/bin/javac"; break; fi
  done
  if [ -z "$CHESS_JAVAC" ]; then CHESS_JAVA=java; CHESS_JAVAC=javac; fi
fi
export CHESS_JAVA CHESS_JAVAC PROJECT_ROOT
