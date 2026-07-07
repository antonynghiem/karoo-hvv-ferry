# Quick Start Guide - HVV Ferry Extension

**Get up and running in 5 minutes!**

## Prerequisites

✅ macOS (you have this)  
✅ Hammerhead Karoo device (you have this)  
✅ Geofox API credentials (you have this)  
❓ GitHub Personal Access Token with `read:packages` permission  
❓ Java 17 installed

## Step 1: GitHub Token (2 minutes)

1. Go to: https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Name it: `karoo-packages`
4. Check: ☑ `read:packages`
5. Click "Generate token"
6. **Copy the token immediately** (you won't see it again!)

## Step 2: Add Credentials (1 minute)

Edit this file:
```
/Users/exa99e/REPOS/hammerhead/hhv-ferry/gradle.properties
```

Add these two lines (replace with your actual values):
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN_FROM_STEP_1
```

**Save the file!**

## Step 3: Build (2 minutes)

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew assembleDebug
```

**First build may take 2-5 minutes** (downloads dependencies).

Look for:
```
BUILD SUCCESSFUL in 3m 42s
```

Your APK is at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Step 4: Install on Karoo

### Option A: ADB (Recommended if you have it)

```bash
# Install ADB if needed
brew install android-platform-tools

# Connect Karoo via USB, enable USB debugging on Karoo

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Option B: Manual

1. Copy `app/build/outputs/apk/debug/app-debug.apk` to USB drive
2. Transfer to Karoo
3. Use Karoo file manager to install

## Step 5: Configure & Test (1 minute)

1. Open "HVV Ferry Times" app on Karoo
2. Enter your Geofox credentials
3. Click "Test Connection"
4. Should see: "✓ Connected: ..."

## Step 6: Add to Karoo Profile

1. Edit your ride profile
2. Select any data field position
3. Choose: **HVV Ferry** → **Next Ferry**
4. Done!

## Step 7: Test It!

Start a ride near a ferry stop and you should see:
```
62 → Finkenwerder  18:34 (450m)
```

---

## Troubleshooting

### "Could not find io.hammerhead:karoo-ext"
→ Check `gpr.user` and `gpr.key` in `gradle.properties`

### "JAVA_HOME not set"
```bash
# Install Java 17
brew install openjdk@17

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### "Connection failed" in app
→ Double-check Geofox username/password  
→ Verify Karoo has internet

### No ferry times showing
→ Wait for GPS to acquire  
→ Check you're near a ferry stop (<10km)

---

## What You Get

✅ Real-time ferry departures from Geofox API  
✅ GPS auto-detection of nearest stops  
✅ Shows next 1 or 2 departures  
✅ Skips cancelled ferries automatically  
✅ Color-coded delays (orange = delayed)  
✅ Distance to ferry stop  
✅ Full-screen departure list  
✅ Secure credential storage  

---

## Ferry Lines Supported

- Line 62: Landungsbrücken ↔ Finkenwerder
- Line 64: Teufelsbrück ↔ Finkenwerder (seasonal)
- Line 68: Landungsbrücken ↔ Neumühlen/Övelgönne
- Line 72: Landungsbrücken ↔ Ernst-August-Kanal
- Line 73: Ernst-August-Kanal ↔ Elbphilharmonie
- Line 75: Landungsbrücken ↔ Altona Dockland

---

**That's it! You're ready to ride!** 🚴‍♂️⛴️

For more details, see:
- `README.md` - Project overview
- `SETUP.md` - Detailed setup guide
- `BUILD_SUMMARY.md` - Complete feature list
- `STATUS.md` - Implementation status
