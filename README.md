# Antigravity Location Tracker

A robust, "set-and-forget" location tracking application designed for elderly users. Built with native Kotlin, Jetpack Compose, and Google Sheets integration.

## Features

- 🔐 **Zero-Trust Authentication**: Google Sign-In required before any functionality
- 📊 **Google Sheets Integration**: Location data syncs directly to your Google Drive
- 📍 **Offline-First**: Locations stored locally, synced when internet available
- 🔋 **Battery Optimized**: Foreground service with WorkManager for reliable tracking
- 🎨 **Elderly-Friendly UI**: Large touch targets, high contrast text, pastel aesthetic

## Setup

### Prerequisites

1. Android Studio Hedgehog (2023.1.1) or later
2. Google Cloud Project with:
   - Google Sheets API enabled
   - Google Drive API enabled
   - OAuth 2.0 Client ID (Android type)

### Google Cloud Configuration

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable APIs:
   - Google Sheets API
   - Google Drive API
4. Configure OAuth consent screen
5. Create OAuth 2.0 Client ID:
   - Application type: Android
   - Package name: `com.antigravity.locationtracker`
   - SHA-1 fingerprint: Get from your keystore

### Get SHA-1 Fingerprint

For debug builds:
```bash
keytool -keystore ~/.android/debug.keystore -list -v -storepass android
```

For release builds:
```bash
keytool -keystore /path/to/your/release-keystore.jks -list -v
```

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/antigravity/locationtracker/
├── AntigravityApp.kt          # Application class
├── MainActivity.kt            # Single activity, navigation host
├── auth/
│   └── GoogleAuthManager.kt   # Google Sign-In handling
├── data/
│   ├── db/                    # Room database
│   ├── prefs/                 # EncryptedSharedPreferences
│   └── sheets/                # Google Sheets API
├── location/
│   └── LocationForegroundService.kt
├── receiver/
│   └── BootReceiver.kt        # Auto-start after reboot
├── sync/
│   └── SyncWorker.kt          # WorkManager sync
└── ui/
    ├── screens/               # Compose screens
    └── theme/                 # Material3 theme
```

## Architecture

- **Offline-First**: All locations saved to Room database immediately
- **Background Sync**: WorkManager uploads to Sheets when network available
- **Foreground Service**: Persistent notification for reliable tracking
- **Zero-Trust**: App is unusable until Google Sign-In complete

## GitHub Actions

The workflow builds:
- Debug APK on all pushes
- Signed Release APK on main branch (requires secrets)

### Required Secrets for Release

- `KEYSTORE_BASE64`: Base64-encoded keystore file
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password

## License

Private - Antigravity
