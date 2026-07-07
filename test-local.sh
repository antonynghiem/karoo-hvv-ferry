#!/bin/bash

echo "🚀 Starting HVV Ferry Local Test..."
echo ""

# Set up environment
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Check if emulator is already running
if adb devices | grep -q "emulator"; then
    echo "✅ Emulator already running"
else
    echo "📱 Launching Android emulator..."
    echo "   (This will open in a new window - wait ~30 seconds for boot)"
    emulator -avd HVVFerryTest -no-snapshot-load > /dev/null 2>&1 &
    
    echo "⏳ Waiting for emulator to boot..."
    adb wait-for-device
    sleep 10  # Extra time for full boot
fi

echo ""
echo "📦 Building and installing app..."
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew installDebug --quiet

echo ""
echo "✅ App installed!"
echo ""
echo "🎯 What you can test:"
echo "   1. Open 'HVV Ferry Times' app in emulator"
echo "   2. Enter Geofox API credentials"
echo "   3. Search for ferry stops"
echo "   4. View departure times"
echo ""
echo "📊 View live logs:"
echo "   adb logcat | grep -E '(🚢|🛑|HvvFerry|Ferry)'"
echo ""
echo "🛑 To stop emulator:"
echo "   adb emu kill"
echo ""
