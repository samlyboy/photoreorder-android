#!/usr/bin/env bash
# 构建 release APK：需要 JDK 17+ 与 Android SDK（platform 34 / build-tools 34.0.0）
set -e
cd "$(dirname "$0")/.."

: "${JAVA_HOME:=D:/Program Files/Android/Android Studio/jbr}"
: "${ANDROID_HOME:=E:/AndroidSDK}"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

GRADLE="${GRADLE:-/d/ai-tools/gradle/gradle-8.9/bin/gradle}"
"$GRADLE" assembleRelease --no-daemon --console=plain

APK=$(ls -t app/build/outputs/apk/release/*.apk | head -1)
mkdir -p dist
cp "$APK" dist/
echo "---- 产物 ----"
ls -lh "dist/$(basename "$APK")"
