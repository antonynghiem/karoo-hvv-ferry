#!/bin/bash

# Test HVV Ferry Extension in Android Emulator
# Usage: ./test-emulator.sh

set -e

export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

APK_PATH="/Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk"
AVD_NAME="HVVFerryTest"

echo "🚀 Starting Android Emulator for HVV Ferry Testing..."
echo ""

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at: $APK_PATH"
    echo "Run: ./gradlew assembleDebug"
    exit 1
fi

echo "✅ APK found: $APK_PATH"
echo ""

# Launch emulator in background
echo "📱 Launching emulator: $AVD_NAME"
echo "   (This will open in a new window...)"
emulator -avd "$AVD_NAME" \
    -no-snapshot-load \
    -wipe-data \
    -no-audio \
    -gpu auto &

EMULATOR_PID=$!
echo "   Emulator PID: $EMULATOR_PID"
echo ""

# Wait for device to boot
echo "⏳ Waiting for emulator to boot..."
adb wait-for-device
echo "   Device detected, waiting for boot to complete..."

# Wait for boot to complete (check for boot_completed property)
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    echo "   Still booting..."
    sleep 2
done

echo "✅ Emulator fully booted!"
echo ""

# Install APK
echo "📦 Installing HVV Ferry APK..."
adb install -r "$APK_PATH"

echo ""
echo "✅ SUCCESS! App installed on emulator"
echo ""
echo "📋 Next steps:"
echo "   1. Open 'HVV Ferry Times' app on the emulator"
echo "   2. Enter your Geofox credentials"
echo "   3. Test the connection"
echo "   4. (Optional) Set mock GPS location to Hamburg ferry stop"
echo ""
echo "📍 Mock GPS Locations for Testing:"
echo "   Landungsbrücken: 53.5457, 9.9664"
echo "   Finkenwerder:    53.5357, 9.8872"
echo ""
echo "   To set: Click '...' button in emulator → Location → Enter coords"
echo ""
echo "🔍 View logs:"
echo "   adb logcat | grep -i hvvferry"
echo ""
echo "🛑 To stop emulator:"
echo "   adb emu kill"
echo "   # or: kill $EMULATOR_PID"
echo ""
