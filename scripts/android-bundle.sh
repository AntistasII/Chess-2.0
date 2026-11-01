#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
JAVA_HOME=$(dirname "$(dirname "$CHESS_JAVA")")
export JAVA_HOME
ANDROID_HOME=${ANDROID_HOME:-"$PROJECT_ROOT/.tools/android-sdk"}
ANDROID_USER_HOME="$PROJECT_ROOT/.tools/android-user"
GRADLE_USER_HOME="$PROJECT_ROOT/.tools/gradle-user"
export ANDROID_HOME ANDROID_USER_HOME GRADLE_USER_HOME
npm --prefix frontend run build
sh android/gradlew -p android --no-daemon bundleRelease lintRelease
mkdir -p artifacts
cp android/app/build/outputs/bundle/release/app-release.aab artifacts/chess-release.aab
"$JAVA_HOME/bin/jarsigner" -verify artifacts/chess-release.aab
printf '\nAAB: %s/artifacts/chess-release.aab\n' "$PROJECT_ROOT"
