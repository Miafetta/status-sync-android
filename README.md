# Status Sync Android

<p align="center">
  <img src="design/sync-card-logo-preview.png" width="120" alt="Status Sync icon">
</p>

<p align="center">
  <strong>Status Sync</strong>
</p>

<p align="center">
  English | <a href="README.zh-CN.md">Simplified Chinese</a>
</p>

Status Sync Android is a small Android application for publishing the current state of a personal phone to a self-hosted endpoint. It is designed for personal homepages, dashboards, and status pages that need to display whether a device is online, what network it is using, and other user-controlled status information.

The app uses Shizuku to read selected system state, shows the payload before upload, and sends JSON data to an API configured by the user.

## Features

- Read device status through Shizuku.
- Preview the JSON payload before it is uploaded.
- Upload once manually or keep uploading on a custom interval.
- Configure the server API URL in the app.
- Configure an optional upload token for Bearer authentication.
- Set a display delay, for example making new status visible after 5 minutes.
- Enable private mode, replacing public status fields with a configured private status message.
- Follow system light/dark mode and Material You dynamic color where available.
- Use a Material-style interface inspired by Shizuku's clear hierarchy and settings flow.

## Requirements

- Android 12 or later.
- Shizuku installed and running.
- Shizuku permission granted to Status Sync.
- A server endpoint that accepts JSON status uploads.

Status Sync does not include a backend implementation. You need to provide your own API and decide how the uploaded status is stored, authenticated, and displayed.

## Data

The app currently uploads the following JSON fields:

```json
{
  "model": "device model",
  "battery_raw": "raw battery status",
  "window_raw": "focused window or foreground app information",
  "wifi_raw": "Wi-Fi status",
  "net_raw": "cellular network type",
  "location_raw": "last location output",
  "private_mode": false,
  "display_delay_minutes": 0,
  "visible_after_unix_ms": 1710000000000
}
```

When private mode is enabled, public status fields are replaced with the app's private status message.

## Server API

The default upload URL is:

```text
https://api.yourdomain.com/api/upload_raw
```

You can change it in the app settings.

The app sends a `POST` request with a JSON body:

```http
POST /api/upload_raw
Content-Type: application/json
Authorization: Bearer <upload-token>
```

The upload token is optional. If it is empty, the app does not attach the `Authorization` header.

The token is used only as an authentication credential for your server. It does not encrypt the JSON body. If the uploaded status contains private information, use HTTPS and handle access control on your server.

## Privacy

Status Sync can read and upload raw device status, including device model, battery output, focused window information, network state, and recent location output. Review the payload preview before enabling automatic upload.

Only upload to a server you control and trust. Avoid exposing raw payloads directly on a public page unless you have filtered sensitive content.

## Build

Clone the repository and build with the Gradle Wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests with:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Project Structure

```text
app/src/main/java/com/miafetta/statussyncandroid/
  AppSettings.kt             App settings stored in SharedPreferences
  DeviceStatusCollector.kt   Shizuku-based status collection
  MainActivity.kt            Main status and upload screen
  SettingsActivity.kt        Settings and Shizuku permission screen
  StatusWorker.kt            Background upload worker
```

## Tech Stack

- Kotlin
- Android Jetpack
- Material Components / Material 3
- Shizuku API
- WorkManager
- OkHttp

## Permissions

The app declares:

- `INTERNET`
- `ACCESS_NETWORK_STATE`

System status collection depends on Shizuku. The user must start Shizuku and explicitly grant permission to this app.

## License

No open-source license has been selected yet. Until a license is added, all rights are reserved by the repository owner.
