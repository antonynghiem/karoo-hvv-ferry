# HVV Ferry Times - Karoo Extension

A Hammerhead Karoo extension that displays real-time HVV ferry departure times on your bike computer.

## Features

- **Real-time ferry departures** from Geofox API
- **GPS auto-detection** of nearest ferry stops (500m - 10km radius)
- **Configurable display** formats (full/abbreviated/direction-only)
- **Two-departure mode** (show next + one after)
- **Smart filtering** - skip cancelled departures
- **Color coding** - white (normal), orange (delayed)
- **Service announcements** for disruptions
- **Secure credential storage** with Android Keystore

## Setup

### 1. GitHub Packages Authentication

Create or edit `gradle.properties` and add:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

### 2. Geofox API Credentials

You'll need to enter your Geofox API credentials in the app's configuration screen after installation.

### 3. Build

```bash
./gradlew assembleDebug
```

### 4. Install on Karoo

```bash
./gradlew installDebug
```

Or manually install the APK from `app/build/outputs/apk/debug/app-debug.apk`

## Configuration

1. Open the HVV Ferry Times app on your Karoo
2. Enter your Geofox API credentials
3. Select ferry lines to monitor (62, 64, 68, 72, 73, 75)
4. Configure GPS auto-detection or select a manual stop
5. Choose display format and other preferences

## Usage

Once configured, add the "Next Ferry" data field to your Karoo profile:

1. Edit your Karoo profile
2. Add data field → HVV Ferry → Next Ferry
3. The data field will show: `62 → Finkenwerder  18:34 (450m)`

Tap the data field to view full ferry times screen.

## Ferry Lines

- **Line 62**: Landungsbrücken ↔ Finkenwerder
- **Line 64**: Teufelsbrück ↔ Finkenwerder (seasonal)
- **Line 68**: Landungsbrücken ↔ Neumühlen/Övelgönne
- **Line 72**: Landungsbrücken ↔ Ernst-August-Kanal
- **Line 73**: Ernst-August-Kanal ↔ Elbphilharmonie
- **Line 75**: Landungsbrücken ↔ Altona Dockland

## Technical Details

- **Platform**: Android 8.1+ (API 27+)
- **Language**: Kotlin
- **Architecture**: MVVM with Hilt DI
- **UI**: Jetpack Compose
- **API**: HVV Geofox GTI (REST)
- **Database**: Room (for ferry stop cache)
- **HTTP Client**: Ktor with OkHttp engine

## Project Structure

```
app/src/main/kotlin/io/hammerhead/hvvferry/
├── api/                    # Geofox API client and models
├── data/
│   ├── database/          # Room database
│   ├── models/            # Data models
│   └── preferences/       # Settings and credentials
├── extension/             # Karoo extension service
├── service/               # Background services
├── ui/                    # Compose UI screens
└── utils/                 # Utilities (formatting, distance calc)
```

## License

Apache 2.0

## Credits

- Hammerhead Karoo SDK
- HVV Geofox API
- Ktor HTTP Client
