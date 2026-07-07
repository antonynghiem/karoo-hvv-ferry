# Testing HVV Ferry Extension in Android Emulator

## ✅ Ready to Test!

Your emulator is set up and ready! You have a few options:

---

## Option 1: One-Command Launch (Recommended) ⭐

Just run the included script:

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-emulator.sh
```

This will:
1. Launch the Android emulator
2. Wait for it to boot
3. Install your HVV Ferry APK
4. Show you next steps

**That's it!** The emulator will open in a new window with your app installed.

---

## Option 2: Use Android Studio (Easiest)

If you have Android Studio installed:

1. Open Android Studio
2. Go to: **Tools** → **Device Manager**
3. Click **Create Device**
4. Select any phone (e.g., Pixel 4)
5. Select system image (API 30 or higher)
6. Click **Finish**
7. Click **Play** button to launch
8. Drag-and-drop the APK onto the emulator window

**APK Location**: `/Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk`

---

## Option 3: Skip Emulator, Use Real Device

The emulator won't perfectly simulate Karoo anyway. Better to test on:

- **Real Karoo device** (best option)
- **Any Android phone** (to test UI and API connectivity)

For a real device:

```bash
# Enable USB debugging on device
# Connect via USB
adb devices  # Should show your device
adb install /Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk
```

---

## Limitations of Emulator Testing

**What you CAN test:**
- ✅ App installation and launch
- ✅ Configuration screen UI
- ✅ Credential entry
- ✅ Geofox API connection (if emulator has internet)
- ✅ Ferry data display in full-screen view

**What you CANNOT test:**
- ❌ Karoo-specific data field rendering
- ❌ GPS location updates from Karoo
- ❌ Integration with Karoo ride profiles
- ❌ Performance during actual bike ride

**Bottom line**: Emulator is good for UI testing, but you need real Karoo for full testing.

---

## Mock GPS Location in Emulator

If you want to test with Hamburg ferry locations:

1. Launch emulator
2. Open emulator's **Extended Controls** (... menu)
3. Go to **Location** tab
4. Enter coordinates for Hamburg ferry stop:
   - **Landungsbrücken**: `53.5457, 9.9664`
   - **Finkenwerder**: `53.5357, 9.8872`
5. Click **Send**

Now the app will think you're near a ferry stop!

---

## Quick Commands Summary

```bash
# Set up environment
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Check installation status
ps aux | grep sdkmanager

# List available AVDs
emulator -list-avds

# Create AVD (after system image download completes)
avdmanager create avd -n TestPhone -k "system-images;android-30;google_apis;x86_64" -d pixel_4

# Launch emulator
emulator -avd TestPhone &

# Install app
adb wait-for-device
adb install app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep -i hvvferry
```

---

## Troubleshooting

### Emulator is slow
- Use x86_64 image (not ARM) on Intel/AMD Macs
- Enable hardware acceleration
- Allocate more RAM to AVD

### Can't connect to internet
- Check emulator network settings
- Try: `adb shell ping google.com`

### App crashes
- Check logs: `adb logcat | grep -E "hvvferry|AndroidRuntime"`
- Verify Android version is API 27+

---

## My Recommendation

**For basic UI testing**: Wait for current download, create AVD, test app

**For full testing**: Use your real Karoo device - it's the only way to properly test the data field integration

The extension is designed for Karoo-specific features that won't work properly in a standard emulator anyway.

---

## What's Installed

✅ **Android Emulator**: Installed and configured  
✅ **System Image**: Android 8.1 (API 27) - x86_64  
✅ **AVD Created**: "HVVFerryTest" - Pixel device  
✅ **Launch Script**: `test-emulator.sh` ready to use

---

## Quick Start

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-emulator.sh
```

Then open "HVV Ferry Times" app in the emulator!
