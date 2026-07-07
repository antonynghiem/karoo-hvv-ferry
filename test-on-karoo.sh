#!/bin/bash

echo "🏁 Karoo Testing Script"
echo ""

# Check Karoo is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Karoo detected. Please:"
    echo "   1. Connect Karoo via USB"
    echo "   2. Enable USB debugging on Karoo:"
    echo "      Settings → About → Tap 'Build Number' 7 times"
    echo "      Settings → Developer Options → Enable USB Debugging"
    echo "   3. Allow USB debugging prompt on Karoo screen"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "✅ Karoo detected"
echo ""

# Build and install
echo "📦 Building and installing app..."
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew installDebug --quiet

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App installed on Karoo successfully!"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎯 TESTING INSTRUCTIONS"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📱 On your Karoo device:"
    echo ""
    echo "  Step 1: Configure the app"
    echo "    • Open 'HVV Ferry Times' app"
    echo "    • Enter your Geofox credentials"
    echo "    • Tap 'Test Connection'"
    echo "    • Select a ferry stop (e.g., Landungsbrücken)"
    echo ""
    echo "  Step 2: Add data field to profile"
    echo "    • Go to Profiles"
    echo "    • Edit a profile"
    echo "    • Add 'Next Ferry Departure' data field"
    echo "    • Save profile"
    echo ""
    echo "  Step 3: Test visibility detection"
    echo "    • Navigate TO page with ferry field"
    echo "      → Should see '🚢 VISIBLE' below"
    echo "    • Navigate AWAY from ferry field"
    echo "      → Should see '🛑 HIDDEN' below"
    echo "    • Navigate back"
    echo "      → Should see '🚢 VISIBLE' again"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📊 LIVE LOGS (watch for visibility changes)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "Key things to look for:"
    echo "  🚢 = Data field became VISIBLE (polling starts)"
    echo "  🛑 = Data field became HIDDEN (polling stops)"
    echo "  ✅ = Streaming data to Karoo"
    echo "  📡 = API call made"
    echo "  💾 = Using cached data (no API call)"
    echo "  ⏰ = Skipped update (battery optimization)"
    echo ""
    sleep 2
    adb logcat -c  # Clear old logs
    adb logcat | grep --line-buffered -E "(🚢|🛑|HvvFerry|Streaming|Fetched|Skipping|cached)"
else
    echo ""
    echo "❌ Installation failed"
    echo "Check error messages above"
    exit 1
fi
