# HVV Ferry Extension - Setup Guide

## Prerequisites

1. **Java Development Kit (JDK) 17**
   ```bash
   java -version  # Should show version 17 or higher
   ```

2. **GitHub Personal Access Token**
   - Go to: https://github.com/settings/tokens
   - Create new token with `read:packages` permission
   - Save the token securely

3. **Geofox API Credentials**
   - You mentioned you have these
   - Username and password from HVV

## Step 1: Configure GitHub Packages

Edit `gradle.properties` and add your GitHub credentials:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

**Important**: Never commit `gradle.properties` with real credentials!

## Step 2: Verify Project Structure

Your project should look like this:

```
hhv-ferry/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/io/hammerhead/hvvferry/
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew
```

## Step 3: Build the Project

From the project root (`hhv-ferry` directory):

```bash
# Make gradlew executable (if not already)
chmod +x ./gradlew

# Build debug APK
./gradlew assembleDebug
```

This will:
- Download Gradle and dependencies (first time only)
- Download karoo-ext library from GitHub Packages
- Compile the Kotlin code
- Generate APK at: `app/build/outputs/apk/debug/app-debug.apk`

**Expected build time**: 2-5 minutes (first build), 30-60 seconds (subsequent builds)

## Step 4: Transfer APK to Karoo

### Option A: ADB (if Karoo is USB-connected)

```bash
# Install ADB if you don't have it
# macOS: brew install android-platform-tools

# Connect Karoo via USB and enable USB debugging on Karoo

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Option B: Manual Transfer

1. Copy `app/build/outputs/apk/debug/app-debug.apk` to a USB drive or cloud storage
2. Transfer to Karoo
3. Use a file manager app on Karoo to install the APK

## Step 5: Initial Configuration

1. **Open the app** on your Karoo:
   - Look for "HVV Ferry Times" in your apps

2. **Enter Geofox credentials**:
   - Username: (your Geofox API username)
   - Password: (your Geofox API password)

3. **Test connection**:
   - Click "Test Connection"
   - You should see "✓ Connected: ..." message

## Step 6: Add Data Field to Karoo Profile

1. Edit your Karoo ride profile
2. Select a data field position
3. Choose: **HVV Ferry** → **Next Ferry**
4. Start a ride and the ferry times should appear!

## Troubleshooting

### Build Fails with "Could not find io.hammerhead:karoo-ext"

**Cause**: GitHub Packages authentication issue

**Fix**:
1. Verify `gpr.user` and `gpr.key` in `gradle.properties`
2. Ensure your token has `read:packages` permission
3. Try: `./gradlew clean build`

### Build Fails with "JAVA_HOME not set"

**Cause**: JDK not found

**Fix**:
```bash
# macOS (if using Homebrew)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Or install JDK 17
brew install openjdk@17
```

### "Connection failed" in the app

**Causes**:
1. Wrong Geofox credentials
2. No internet connection
3. Geofox API is down

**Fix**:
1. Double-check credentials
2. Verify Karoo has internet (WiFi or mobile data)
3. Test Geofox API at: https://gti.geofox.de/

### No ferry times showing

**Causes**:
1. No ferry stops nearby (GPS issue)
2. No credentials entered
3. Extension not enabled

**Fix**:
1. Check if you're near a ferry stop (<10km by default)
2. Re-enter credentials in app
3. Restart Karoo

### Data field shows "No ferries nearby"

**Normal if**:
- You're >10km from any ferry stop
- No ferries running (late night/early morning)
- GPS hasn't acquired location yet

**Try**:
- Wait for GPS to acquire (green icon on Karoo)
- Check ferry operating hours
- Manually select a ferry stop in app (future feature)

## Development Tips

### Rebuild after code changes

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### View logs

```bash
adb logcat | grep -i "hvvferry\|geofox\|ferry"
```

### Check installed version

```bash
adb shell pm list packages | grep hvvferry
adb shell dumpsys package io.hammerhead.hvvferry | grep version
```

### Uninstall

```bash
adb uninstall io.hammerhead.hvvferry
```

## Next Steps

Once the basic version is working:

1. **Add full configuration UI** (ferry line selection, GPS settings, etc.)
2. **Build station cache** (download all HVV ferry stops)
3. **Add full-screen ferry times view**
4. **Implement background updates**
5. **Add service announcements**

## Support

- **Karoo Extension SDK**: https://hammerheadnav.github.io/karoo-ext/
- **Geofox API Docs**: https://gti.geofox.de/
- **Project Issues**: Check STATUS.md for known issues

## Files to Check

- `STATUS.md` - Implementation status
- `README.md` - Project overview
- `SETUP.md` - This file
- `app/build.gradle.kts` - Dependencies and build config

---

Good luck! 🚴‍♂️⛴️
