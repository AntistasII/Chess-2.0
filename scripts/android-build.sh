#!/bin/sh
set -eu
. "$(dirname "$0")/java-env.sh"
cd "$PROJECT_ROOT"
JAVA_HOME=$(dirname "$(dirname "$CHESS_JAVA")")
export JAVA_HOME
ANDROID_HOME=${ANDROID_HOME:-"$PROJECT_ROOT/.tools/android-sdk"}
export ANDROID_HOME
ANDROID_USER_HOME="$PROJECT_ROOT/.tools/android-user"
export ANDROID_USER_HOME
GRADLE_USER_HOME="$PROJECT_ROOT/.tools/gradle-user"
export GRADLE_USER_HOME
npm --prefix frontend run build
sh android/gradlew -p android --no-daemon assembleDebug lintDebug
mkdir -p artifacts
cp android/app/build/outputs/apk/debug/app-debug.apk artifacts/chess-android.apk
cp artifacts/chess-android.apk artifacts/chess-offline.apk
printf '\nAPK: %s/artifacts/chess-android.apk\n' "$PROJECT_ROOT"
