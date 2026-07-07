# Testing HVV Ferry Extension on Hammerhead Karoo

Complete guide for testing the app on a real Karoo device with full visibility detection and battery optimization verification.

---

## 🎯 **Quick Start - 5 Minutes to First Test**

```bash
# 1. Connect Karoo via USB
# 2. Enable Developer Mode on Karoo (see below)
# 3. Run this:
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew installDebug
adb logcat | grep -E "(🚢|🛑|HvvFerry|Ferry)"
```

Now use your Karoo and watch the logs show visibility detection in real-time!

---

## 📱 **Step 1: Enable Developer Mode on Karoo**

### On Karoo Device:

1. **Open Settings** (swipe down from top)
2. Go to **About**
3. Tap **Build Number** 7 times
   - You'll see: "You are now a developer!"
4. Go back to **Settings**
5. Go to **Developer Options** (new menu)
6. Enable **USB Debugging**
7. Enable **Stay Awake** (optional, helpful for testing)

### On Your MacBook:

Connect Karoo via USB cable, then:

```bash
# Check Karoo is detected
adb devices
```

**Expected output:**
```
List of devices attached
AB12CD34EF56    device
```

If you see "unauthorized", check Karoo screen for "Allow USB debugging?" prompt and tap **Allow**.

---

## 🚀 **Step 2: Install the App**

### Option A: One Command (Recommended)

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew installDebug
```

### Option B: Manual Install

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Verify Installation

On Karoo:
- Swipe up from home screen
- Look for **"HVV Ferry Times"** app icon
- Should appear in app list

---

## ⚙️ **Step 3: Configure the Extension**

### Initial Setup

1. **Open app** on Karoo
2. **Enter Geofox credentials**:
   - Username: (your Geofox API username)
   - Password: (your Geofox API password)
3. **Test connection**: Tap "Test Connection" button
   - Should see: ✅ "Connected successfully"

4. **Select ferry stop**:
   - Tap "Select Ferry Stop"
   - Search for: "Landungsbrücken" (or your preferred stop)
   - Tap to select

5. **Configure settings**:
   - Update interval: 60 seconds (default)
   - Show 2 departures: ON
   - Save settings

---

## 🎨 **Step 4: Add Data Field to Ride Profile**

This is where the magic happens!

### On Karoo:

1. **Go to Ride Profiles**:
   - Home screen → **Profiles**
   - Select a profile (e.g., "Default")

2. **Edit Profile**:
   - Tap **Edit**
   - Tap on any **Data Field** slot

3. **Add Ferry Data Field**:
   - Scroll to find **"HVV Ferry"** section
   - Select **"Next Ferry Departure"**
   - Tap to add

4. **Position the field** where you want it

5. **Save profile**

---

## 🔍 **Step 5: Test Visibility Detection (THE KEY TEST!)**

This is the most important test - verifying that polling **only happens when visible**.

### Start Logging (On MacBook):

```bash
adb logcat | grep -E "(🚢|🛑|HvvFerry|Ferry|Streaming)"
```

### Test Sequence (On Karoo):

#### Test 1: Navigate TO Ferry Field

1. Navigate to the profile page with ferry data field
2. **Watch logs** - you should see:
   ```
   🚢 Ferry data field became VISIBLE - starting smart polling
   ✅ Streaming: Next ferry in 5 minutes
   📡 Fetched 3 departures for Landungsbrücken - cached
   ```

✅ **Success**: Polling started when field became visible!

---

#### Test 2: Navigate AWAY from Ferry Field

1. Swipe to a different profile page (without ferry field)
2. **Watch logs** - you should see:
   ```
   🛑 Ferry data field became HIDDEN - stopping polling to save battery
   ```

✅ **Success**: Polling stopped immediately!

---

#### Test 3: Navigate BACK to Ferry Field

1. Swipe back to page with ferry field
2. **Watch logs** - you should see:
   ```
   🚢 Ferry data field became VISIBLE - starting smart polling
   ```

✅ **Success**: Polling resumed automatically!

---

#### Test 4: Verify NO Polling When Hidden

1. Stay on a page **without** ferry field for 2-3 minutes
2. **Watch logs** - you should see:
   - **NO** "Streaming" messages
   - **NO** "Fetched departures" messages
   - **NO** API calls

✅ **Success**: Zero API calls when field is not visible!

---

## 🔋 **Step 6: Test Battery Optimizations**

### Test Service Hours Check

**If testing at night (11pm-5am):**

1. Navigate to ferry field
2. **Watch logs** - you should see:
   ```
   🚢 Ferry data field became VISIBLE
   ⏰ Skipping update: outside service hours
   ```

✅ **Success**: No API calls during off-hours!

**To test during day**: Temporarily edit the code to reverse the hours check.

---

### Test Response Caching

1. Navigate to ferry field (starts polling)
2. Wait for first API call:
   ```
   📡 Fetched 3 departures for Landungsbrücken - cached
   ```
3. Within 30 seconds, navigate away then back
4. **Watch logs** - you should see:
   ```
   🚢 Ferry data field became VISIBLE
   💾 Using cached departures for stop 12345
   ```

✅ **Success**: Cache prevents duplicate API call!

---

### Test Network Connectivity Check

1. **Turn OFF Karoo WiFi/Data**:
   - Settings → WiFi → OFF
2. Navigate to ferry field
3. **Watch logs** - you should see:
   ```
   🚢 Ferry data field became VISIBLE
   📡 Skipping update: no network connectivity
   ```

✅ **Success**: No wasted API attempts when offline!

---

### Test Exponential Backoff

**Simulate API errors** (requires code change):

1. Temporarily enter wrong credentials
2. Navigate to ferry field
3. **Watch logs** - you should see:
   ```
   ❌ Error updating ferry data
   ⏰ Using backoff delay: 120s (failures: 1)
   ⏰ Using backoff delay: 240s (failures: 2)
   ⏰ Using backoff delay: 480s (failures: 3)
   ```

✅ **Success**: Polling slows down exponentially on errors!

---

## 📊 **Step 7: Monitor Battery Usage**

### Real-World Battery Test

**Setup:**
1. Fully charge Karoo (100%)
2. Install app
3. Configure ferry stop
4. Add data field to profile

**Test A: Heavy Use (Field Always Visible)**
1. Start a ride
2. Stay on ferry field page for 1 hour
3. Note battery level
4. **Expected**: ~1-2% battery drain per hour

**Test B: Light Use (Occasional Viewing)**
1. Start a ride
2. Mostly view other pages (speed, map, etc.)
3. Check ferry field 3-4 times (2 min each)
4. Ride for 1 hour total
5. Note battery level
6. **Expected**: ~0.2-0.5% battery drain per hour

**Test C: No Use (Field Not Visible)**
1. Start a ride
2. Never view ferry field page
3. Ride for 1 hour
4. Note battery level
5. **Expected**: ~0% battery drain (no polling!)

---

## 🎬 **Step 8: Test Bonus Action**

The bonus action shows full-screen ferry times.

### Test Procedure:

1. **Start a ride** (or just be on home screen)
2. **Open actions menu**:
   - Tap hamburger menu (top left)
   - Or swipe from left edge
3. Look for **"Show Ferry Times"** action
4. **Tap it**
5. Should see **full-screen ferry departure list**

✅ **Success**: Full screen shows all departures with times!

---

## 📝 **Complete Test Checklist**

Use this to verify everything works:

### Installation
- [ ] Developer mode enabled on Karoo
- [ ] USB debugging enabled
- [ ] `adb devices` shows Karoo
- [ ] App installs successfully
- [ ] App appears in Karoo app list

### Configuration
- [ ] Can open HVV Ferry Times app
- [ ] Can enter credentials
- [ ] "Test Connection" succeeds
- [ ] Can search for ferry stops
- [ ] Can select a ferry stop
- [ ] Settings save correctly

### Data Field Integration
- [ ] Can add "Next Ferry Departure" to profile
- [ ] Data field appears on profile page
- [ ] Shows ferry data (time or minutes)
- [ ] Updates periodically
- [ ] Shows 2 departures if configured

### Visibility Detection (CRITICAL!)
- [ ] Logs show "🚢 VISIBLE" when navigating to field
- [ ] Logs show "🛑 HIDDEN" when navigating away
- [ ] "Streaming" messages only appear when visible
- [ ] NO API calls when field is hidden
- [ ] Polling resumes when returning to field

### Battery Optimizations
- [ ] Cache working (💾 messages in logs)
- [ ] Service hours respected (⏰ messages at night)
- [ ] Network checks working (📡 messages when offline)
- [ ] Exponential backoff on errors
- [ ] Low battery drain during real ride

### Bonus Features
- [ ] Bonus action appears in menu
- [ ] Full-screen ferry times work
- [ ] Shows current departures
- [ ] Updates when refreshed

---

## 🔍 **Detailed Log Analysis**

### Expected Log Flow

**When navigating TO ferry field:**
```
D HvvFerryExtension: 🚢 Ferry data field became VISIBLE - starting smart polling
D FerryDataType: Checking shouldUpdate()
D FerryDataType: ✅ During service hours
D FerryDataType: ✅ Network connected
D FerryDataType: 📡 Fetched 3 departures for Landungsbrücken - cached
D FerryDataType: ✅ Streaming: Next ferry in 5 minutes
```

**During periodic updates (every 60s):**
```
D FerryDataType: Checking shouldUpdate()
D FerryDataType: 💾 Using cached departures for stop 12345
D FerryDataType: ✅ Streaming: Next ferry in 4 minutes
```

**After cache expires (>30s):**
```
D FerryDataType: 📡 Fetched 3 departures for Landungsbrücken - cached
D FerryDataType: ✅ Streaming: Next ferry in 3 minutes
```

**When navigating AWAY:**
```
D FerryDataType: 🛑 Ferry data field became HIDDEN - stopping polling to save battery
```

**When hidden (should see NOTHING):**
```
(no messages - no polling happening!)
```

---

## ⚠️ **Troubleshooting**

### App doesn't install

**Error: "INSTALL_FAILED_UPDATE_INCOMPATIBLE"**
```bash
# Uninstall old version first
adb uninstall io.hammerhead.hvvferry
# Then reinstall
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

### Data field shows "---" or blank

**Possible causes:**
1. No credentials configured
2. No ferry stop selected
3. API error
4. Network offline

**Check logs:**
```bash
adb logcat | grep -i error
```

---

### Not seeing visibility logs (🚢/🛑)

**Possible causes:**
1. Field not actually added to profile
2. Logs not capturing emoji characters
3. Wrong typeId in extension_info.xml

**Try:**
```bash
# Broader log filter
adb logcat | grep -i ferry

# Or just FerryDataType
adb logcat | grep FerryDataType
```

---

### Polling doesn't stop when navigating away

**Check:**
1. Ensure you're using the NEW version (with FerryDataType)
2. Check `extension_info.xml` has correct `typeId="ferry-next-departure"`
3. Verify `types` property is properly defined in HvvFerryExtension

**Verify code:**
```bash
grep -n "setCancellable" app/src/main/kotlin/io/hammerhead/hvvferry/extension/FerryDataType.kt
```

Should see line with the stop polling callback.

---

### Battery drains faster than expected

**Check:**
1. Are you actually viewing the ferry field constantly?
2. Is update interval set too low (30s)?
3. Are there API errors causing retries?

**Monitor:**
```bash
adb logcat | grep -E "(Fetched|Streaming|backoff)"
```

Count how many "Fetched" messages you see per minute.

---

### API connection fails

**Check:**
1. Credentials are correct
2. Karoo has internet (WiFi or cellular)
3. Geofox API is online

**Test:**
```bash
adb logcat | grep -i geofox
```

---

## 🎯 **Success Criteria**

Your implementation is working correctly if:

✅ **Visibility Detection:**
- Logs show "🚢 VISIBLE" exactly when you navigate to ferry field
- Logs show "🛑 HIDDEN" exactly when you navigate away
- Zero "Streaming" messages when field is not visible

✅ **Battery Optimization:**
- Cache hits visible in logs (💾 messages)
- No polling at night (⏰ service hours messages)
- No polling when offline (📡 network messages)
- Exponential backoff on errors

✅ **Functional:**
- Data field displays ferry times
- Updates every 60 seconds (when visible)
- Shows correct departure information
- Bonus action works

✅ **Performance:**
- Low battery impact (<2% per hour when visible)
- Minimal battery impact when not visible
- No lag or UI freezing
- Karoo remains responsive

---

## 📊 **Advanced Testing**

### Test Multiple Data Fields

1. Add ferry field to **multiple profile pages**
2. Navigate between them
3. **Expected**: Only one polling instance active at a time
4. Logs should show stop/start as you switch pages

---

### Test During Actual Ride

1. Start a real ride
2. Add ferry field to ride profile
3. Monitor for several hours
4. Check battery impact
5. Verify data accuracy

**Ideal test**: Hamburg harbor cycling route where you actually use ferry data!

---

### Stress Test

1. Rapidly switch between pages with/without ferry field
2. Navigate back and forth 20+ times
3. **Expected**: No crashes, no memory leaks
4. Each navigation should log correctly

---

### Long-Duration Test

1. Leave ferry field visible for 2+ hours
2. Monitor:
   - API call frequency
   - Cache hit rate
   - Battery drain
   - Memory usage

**Expected results:**
- API calls every 60s (or your configured interval)
- ~50% cache hit rate (every other call skipped)
- ~2% battery per hour
- Stable memory (no leaks)

---

## 🚀 **Quick Testing Scripts**

### Create Quick Test Script

```bash
cat > /Users/exa99e/REPOS/hammerhead/hhv-ferry/test-on-karoo.sh << 'EOF'
#!/bin/bash

echo "🏁 Karoo Testing Script"
echo ""

# Check Karoo is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Karoo detected. Please:"
    echo "   1. Connect Karoo via USB"
    echo "   2. Enable USB debugging on Karoo"
    echo "   3. Allow USB debugging prompt"
    exit 1
fi

echo "✅ Karoo detected"
echo ""

# Build and install
echo "📦 Building and installing..."
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./gradlew installDebug --quiet

echo ""
echo "✅ App installed on Karoo!"
echo ""
echo "🎯 Now on your Karoo:"
echo "   1. Open 'HVV Ferry Times' app"
echo "   2. Configure credentials and ferry stop"
echo "   3. Add 'Next Ferry Departure' to a ride profile"
echo "   4. Navigate to/from the data field"
echo ""
echo "📊 Watch logs here (live updates):"
echo ""
adb logcat -c  # Clear old logs
adb logcat | grep -E "(🚢|🛑|HvvFerry|Streaming|Fetched)"
EOF

chmod +x /Users/exa99e/REPOS/hammerhead/hhv-ferry/test-on-karoo.sh
```

### Run It

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-on-karoo.sh
```

---

## 📸 **What You Should See**

### On Karoo Screen

**Data Field (when visible):**
```
┌─────────────────────┐
│ Next Ferry: 5 min   │
│ Line 62 → Finken.   │
└─────────────────────┘
```

**Full Screen (bonus action):**
```
┌────────────────────────────┐
│  Landungsbrücken Ferries   │
├────────────────────────────┤
│ 14:23  Line 62  5 min      │
│        → Finkenwerder      │
│                            │
│ 14:38  Line 62  20 min     │
│        → Finkenwerder      │
└────────────────────────────┘
```

### In Logs (MacBook Terminal)

**Perfect visibility detection:**
```
14:20:15.123 D 🚢 Ferry data field became VISIBLE - starting smart polling
14:20:15.456 D ✅ During service hours
14:20:15.789 D ✅ Network connected
14:20:16.123 D 📡 Fetched 3 departures for Landungsbrücken - cached
14:20:16.456 D ✅ Streaming: Next ferry in 5 minutes

[... 60 seconds later ...]

14:21:16.123 D 💾 Using cached departures for stop 12345
14:21:16.456 D ✅ Streaming: Next ferry in 4 minutes

[... user navigates away ...]

14:22:30.789 D 🛑 Ferry data field became HIDDEN - stopping polling to save battery

[... complete silence for minutes - no polling! ...]

[... user navigates back ...]

14:25:15.123 D 🚢 Ferry data field became VISIBLE - starting smart polling
14:25:15.456 D 📡 Fetched 3 departures for Landungsbrücken - cached
14:25:15.789 D ✅ Streaming: Next ferry in 2 minutes
```

---

## 🎉 **You'll Know It's Working When...**

1. ✅ Data field shows real ferry times from Hamburg
2. ✅ Times update every minute (when visible)
3. ✅ Logs show "🚢 VISIBLE" exactly when you navigate to field
4. ✅ Logs show "🛑 HIDDEN" exactly when you navigate away
5. ✅ **ZERO logs when field is not visible** (this is KEY!)
6. ✅ Battery barely drains when not viewing field
7. ✅ Cache working (see 💾 messages)
8. ✅ Service hours respected at night
9. ✅ No polling when offline

**That's it!** Your visibility-based polling is working perfectly! 🎊

---

## 📞 **Need Help?**

### Check Logs First

```bash
# Full logs
adb logcat | grep -i hvvferry

# Just visibility
adb logcat | grep -E "(🚢|🛑)"

# Just errors
adb logcat | grep -i error | grep -i ferry
```

### Common Issues Reference

| Issue | Solution |
|-------|----------|
| Field shows "---" | Check credentials, network, logs |
| No visibility logs | Check typeId matches, verify FerryDataType |
| Polling doesn't stop | Verify using new version with DataTypeImpl |
| High battery drain | Check if field visible constantly, verify logs |
| API errors | Check credentials, Geofox API status |

---

## 🏁 **Ready to Test!**

**Your app is fully ready for Karoo testing.**

Just run:
```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-on-karoo.sh
```

Then follow the on-screen instructions! 🚀
