# Testing Your HVV Ferry Extension

You now have **3 ways** to test your app:

---

## 🖥️ Option 1: Android Emulator (Just Set Up!)

### Quick Start

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-emulator.sh
```

### What You Can Test
- ✅ App installation and launch
- ✅ Configuration screen UI
- ✅ Credential entry and storage
- ✅ Geofox API connection test
- ✅ Ferry data retrieval and display
- ✅ Mock GPS locations (set via emulator UI)

### Limitations
- ❌ No Karoo-specific data fields
- ❌ No ride profile integration
- ❌ Slower than real device

### Best For
- UI testing
- API integration testing
- Quick iterations during development

---

## 📱 Option 2: Real Android Phone

### Steps

```bash
# Enable USB debugging on your phone:
# Settings → About Phone → Tap "Build Number" 7 times
# Settings → Developer Options → Enable "USB Debugging"

# Connect phone via USB
adb devices

# Install app
adb install /Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk
```

### What You Can Test
- ✅ Everything from Option 1
- ✅ Real GPS location
- ✅ Better performance
- ✅ More realistic testing

### Limitations
- ❌ Still no Karoo-specific features
- ❌ Different screen size than Karoo

### Best For
- GPS-based testing near actual ferry stops
- Performance testing
- Real-world API usage

---

## 🚴 Option 3: Real Karoo Device (Ultimate Test!)

### Installation Methods

#### Via ADB (if available)
```bash
# Enable USB debugging on Karoo:
# Karoo → Settings → About → Tap version 7 times
# Settings → Developer Options → USB Debugging

adb install /Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk
```

#### Via File Transfer
1. Copy `app-debug.apk` to USB drive
2. Plug into Karoo
3. Use file manager to install APK

### Setup on Karoo

1. Open "HVV Ferry Times" app
2. Enter Geofox credentials
3. Test connection
4. Edit ride profile
5. Add data field: **HVV Ferry** → **Next Ferry**
6. Start a ride near a ferry stop!

### What You Can Test
- ✅ **EVERYTHING!**
- ✅ Data field rendering on Karoo screen
- ✅ Real GPS during rides
- ✅ Ride profile integration
- ✅ Actual use case

### Best For
- **Final testing before release**
- **Real-world usage**
- **The actual goal of this project!**

---

## Recommended Testing Flow

1. **Emulator First** (5 min)
   - Launch: `./test-emulator.sh`
   - Test: UI, credentials, API connection
   - Verify: No crashes, data loads

2. **Real Phone** (optional, 10 min)
   - Install on Android phone
   - Walk/bike to Landungsbrücken
   - Test: Real GPS, real ferry data

3. **Karoo Device** (ultimate test)
   - Install on Karoo
   - Configure data field
   - Go for a ride in Hamburg!

---

## Mock Data for Testing

### GPS Coordinates (Hamburg Ferry Stops)

| Stop | Latitude | Longitude | Line |
|------|----------|-----------|------|
| Landungsbrücken | 53.5457 | 9.9664 | 62, 72, 73, 75 |
| Finkenwerder | 53.5357 | 9.8872 | 62, 64 |
| Teufelsbrück | 53.5473 | 9.8656 | 62, 64 |
| Övelgönne | 53.5441 | 9.9017 | 62, 64 |
| Neumühlen | 53.5459 | 9.9251 | 62, 64 |

### Set Mock Location in Emulator

1. Launch emulator: `./test-emulator.sh`
2. Click **"..."** button in emulator toolbar
3. Go to **Location** tab
4. Enter coordinates (e.g., `53.5457, 9.9664`)
5. Click **Send**

Now your app thinks you're at Landungsbrücken!

---

## Viewing Logs

### Emulator or Phone
```bash
adb logcat | grep -i hvvferry
```

### Karoo (via ADB)
```bash
adb logcat | grep -E "hvvferry|AndroidRuntime"
```

### Filter for Errors Only
```bash
adb logcat *:E | grep hvvferry
```

---

## Troubleshooting

### Emulator won't start
```bash
# Check if an emulator is already running
emulator -list-avds
ps aux | grep emulator

# Kill existing emulator
adb emu kill

# Try again
./test-emulator.sh
```

### App won't install
```bash
# Uninstall first
adb uninstall io.hammerhead.hvvferry

# Reinstall
adb install /Users/exa99e/REPOS/hammerhead/hhv-ferry/app/build/outputs/apk/debug/app-debug.apk
```

### Connection to Geofox fails
- Verify credentials are correct
- Check internet connectivity
- Try in emulator vs real device
- Check logs: `adb logcat | grep -i geofox`

### Data field doesn't show on Karoo
- Make sure you added it to your ride profile
- Verify extension service is running
- Check that credentials are saved
- Look at logs for errors

---

## Which Option Should You Use?

| Scenario | Recommended Option |
|----------|-------------------|
| "Just want to see if it works" | 🖥️ **Emulator** |
| "Testing API integration" | 🖥️ **Emulator** |
| "Need real GPS testing" | 📱 **Phone** or 🚴 **Karoo** |
| "Testing UI changes" | 🖥️ **Emulator** |
| "Final verification" | 🚴 **Karoo only** |
| "Actual usage" | 🚴 **Karoo only** |

---

## Quick Reference

### Emulator
```bash
./test-emulator.sh
```

### Phone
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Karoo
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# Then: Add data field to ride profile
```

### Rebuild & Reinstall
```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

**You're all set! Pick your testing method and give it a try!** 🚀
