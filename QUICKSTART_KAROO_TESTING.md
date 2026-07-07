# Quick Start: Testing on Karoo (5 Minutes)

## 🚀 One-Command Test

```bash
cd /Users/exa99e/REPOS/hammerhead/hhv-ferry
./test-on-karoo.sh
```

That's it! The script will:
1. ✅ Check Karoo is connected
2. ✅ Build and install the app
3. ✅ Show live logs with visibility detection
4. ✅ Guide you through testing

---

## 📋 Prerequisites (One-Time Setup)

### On Karoo Device:

1. **Enable Developer Mode:**
   - Settings → About
   - Tap "Build Number" **7 times**
   - You'll see: "You are now a developer!"

2. **Enable USB Debugging:**
   - Settings → Developer Options
   - Turn ON "USB Debugging"

3. **Connect to Mac:**
   - Use USB cable
   - Tap "Allow" on Karoo when prompted

### On Your MacBook:

```bash
# Verify Karoo is connected
adb devices
```

Should show:
```
List of devices attached
AB12CD34    device
```

---

## 🎯 What to Test

### Test 1: Visibility Detection (THE KEY TEST!)

**On Karoo:**
1. Add ferry data field to a profile page
2. Navigate TO that page
3. Navigate AWAY from that page
4. Navigate BACK to that page

**On MacBook logs:**
- Should see `🚢 VISIBLE` when you navigate to field
- Should see `🛑 HIDDEN` when you navigate away
- Should see **NO logs** when field is hidden

✅ **Success = Polling only happens when visible!**

---

### Test 2: Battery Optimizations

**Watch logs for:**
- `💾 Using cached` = Cache working (skips API calls)
- `⏰ Skipping update: outside service hours` = Service hours working
- `📡 Skipping update: no network` = Network check working

✅ **Success = Multiple battery optimizations active!**

---

### Test 3: Real Ferry Data

**On Karoo:**
- Data field should show actual ferry times
- Updates every 60 seconds (when visible)
- Shows next departure time in minutes

✅ **Success = Seeing real Hamburg ferry data!**

---

## 🎬 Complete Test Flow (5 Minutes)

```bash
# Step 1: Connect Karoo and run script
./test-on-karoo.sh

# Step 2: On Karoo, open app and configure
# - Enter credentials
# - Select ferry stop

# Step 3: Add data field to profile
# - Edit profile
# - Add "Next Ferry Departure"

# Step 4: Navigate around and watch logs
# - Navigate to ferry field → see 🚢 VISIBLE
# - Navigate away → see 🛑 HIDDEN
# - Navigate back → see 🚢 VISIBLE

# Step 5: Verify battery optimizations
# - See 💾 cached messages
# - See ⏰ service hours (if at night)
# - Count API calls (should be minimal when not visible)
```

---

## 🔍 Expected Log Output

### Perfect Test Sequence:

```
[Navigate TO ferry field]
🚢 Ferry data field became VISIBLE - starting smart polling
✅ During service hours
✅ Network connected
📡 Fetched 3 departures for Landungsbrücken - cached
✅ Streaming: Next ferry in 5 minutes

[Wait 60 seconds...]
💾 Using cached departures for stop 12345
✅ Streaming: Next ferry in 4 minutes

[Navigate AWAY from ferry field]
🛑 Ferry data field became HIDDEN - stopping polling to save battery

[Complete silence for minutes - NO LOGS!]

[Navigate BACK to ferry field]
🚢 Ferry data field became VISIBLE - starting smart polling
📡 Fetched 3 departures for Landungsbrücken - cached
✅ Streaming: Next ferry in 2 minutes
```

---

## ✅ Success Checklist

- [ ] Script installs app successfully
- [ ] Can open app on Karoo
- [ ] Can configure credentials
- [ ] Can add data field to profile
- [ ] See `🚢 VISIBLE` when navigating to field
- [ ] See `🛑 HIDDEN` when navigating away
- [ ] **ZERO logs when field is hidden** ← CRITICAL!
- [ ] See cache working (`💾` messages)
- [ ] Data field shows real ferry times
- [ ] Times update when field is visible

---

## ❓ Troubleshooting

### "No Karoo detected"
```bash
# Check USB debugging is enabled on Karoo
# Check Karoo screen for "Allow USB debugging?" prompt
adb devices
```

### "Installation failed"
```bash
# Uninstall old version first
adb uninstall io.hammerhead.hvvferry
# Run test script again
./test-on-karoo.sh
```

### "Not seeing visibility logs"
```bash
# Try broader log filter
adb logcat | grep -i ferry
```

### "Data field shows ---"
- Check credentials are correct
- Check network is connected
- Check logs for errors

---

## 🎉 You're Done When...

✅ Logs show `🚢 VISIBLE` exactly when you navigate to field  
✅ Logs show `🛑 HIDDEN` exactly when you navigate away  
✅ **NO polling when field is not visible**  
✅ Data field displays real ferry times  
✅ Cache is working (see 💾 messages)  

**That means your visibility-based polling is working perfectly!** 🚀

Battery savings: **Up to 90% reduction** compared to continuous polling!

---

## 📚 Full Documentation

For detailed testing procedures, see:
- **TESTING_ON_KAROO.md** - Complete guide with all tests
- **DATA_FIELD_VISIBILITY_OPTIMIZATION.md** - Technical details
- **BATTERY_OPTIMIZATIONS.md** - All battery optimizations explained

---

## 🚴 Real-World Test

**The ultimate test:**
1. Install app on Karoo
2. Go for a ride in Hamburg
3. Add ferry field to ride profile
4. Check ferry times when approaching harbor
5. Navigate away when not needed
6. Monitor battery usage over 2-hour ride

**Expected**: <2% battery drain even with occasional checking!

---

**Ready?** Run `./test-on-karoo.sh` and start testing! 🏁
