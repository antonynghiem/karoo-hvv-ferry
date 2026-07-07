#!/bin/bash

# Quick rebuild and reinstall to running emulator/device
# Usage: ./reinstall.sh

set -e

export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

cd /Users/exa99e/REPOS/hammerhead/hhv-ferry

echo "🔨 Building APK..."
./gradlew assembleDebug -q

echo ""
echo "📦 Installing to device/emulator..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "✅ App updated! Open it on your device/emulator."
