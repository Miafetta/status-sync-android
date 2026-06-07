# Status Sync Android

<p align="center">
  <img src="assets/status-sync-android.png" width="120" alt="Status Sync icon">
</p>

<p align="center">
  <strong>Status Sync</strong>
</p>

<p align="center">
  English | <a href="../README.md">Simplified Chinese</a>
</p>

Status Sync Android is an Android status uploader for personal homepages, dashboards, and status pages. It uses Shizuku to collect selected device status, previews the payload locally, and uploads JSON data to a self-hosted API configured by the user.

Current package name: `com.miafetta.statussync`. Current version: `1.2`.

## Features

- Read device model, battery, network, Wi-Fi, location, and foreground app information through Shizuku.
- Preview the actual upload fields on the main screen.
- Upload once manually or keep syncing on a custom interval.
- Configure the server API URL and optional Bearer upload token in the app.
- Configure a display delay, for example making new status visible after 5 minutes.
- Enable private display mode, replacing all uploaded fields with `none`.
- Configure foreground app blacklist/whitelist rules by package name.
- Search and select multiple apps, with an option to show system components.
- Follow system light/dark mode and Material You dynamic color on Android 12+.
- Use a Material-style interface inspired by Shizuku.

## Requirements

- Android 12 or later.
- Shizuku installed and running.
- Shizuku authorization granted to Status Sync.
- A server endpoint that accepts JSON status uploads.

Status Sync does not include a backend implementation. You need to provide your own API and decide how the uploaded status is stored, authenticated, and displayed.

## Data

The app currently uploads the following JSON fields:

```json
{
  "model": "device model",
  "battery_raw": "raw battery status",
  "wifi_raw": "Wi-Fi status",
  "net_raw": "cellular network type",
  "location_raw": "last location output",
  "current_app_package": "current foreground app package",
  "current_app_name": "current foreground app name"
}
```

Settings are applied locally and are not uploaded as JSON fields. When private display mode is enabled, the fields above are uploaded as:

```text
none
```

Foreground app filtering only affects `current_app_package` and `current_app_name`. When a blacklist rule matches, or a whitelist rule does not match, these two fields are uploaded as `none`.

## Server API

The default upload URL is empty and must be configured in the app settings. Example URL:

```text
https://api.yourdomain.com/api/upload_raw
```

The app sends a `POST` request with a JSON body:

```http
POST /api/upload_raw
Content-Type: application/json
Authorization: Bearer <upload-token>
```

The upload token is optional. If it is empty, the app does not attach the `Authorization` header. The token is used only as an authentication credential for your server. It does not encrypt the JSON body. If the uploaded status contains private information, use HTTPS and handle access control on your server.

## Build

Clone the repository and build with the Gradle Wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run verification tasks with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

The default Android Studio sample tests have been removed. These commands verify the test task, resource processing, Kotlin compilation, and lint.

## Project Structure

```text
app/src/main/java/com/miafetta/statussync/
  AppSettings.kt             Local settings persistence
  AppPickerActivity.kt       App picker for foreground filtering
  AppToast.kt                System Toast wrapper
  DeviceStatusCollector.kt   Shizuku-based status collection and upload JSON generation
  MainActivity.kt            Main screen, payload preview, and sync actions
  SettingsActivity.kt        Settings, Shizuku authorization, and filtering rules
  StatusSyncApplication.kt   Dynamic color initialization
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
- `QUERY_ALL_PACKAGES`

`QUERY_ALL_PACKAGES` is used to show selectable apps in foreground filtering settings. System status collection depends on Shizuku. The user must start Shizuku and explicitly grant authorization to this app.

## Privacy

Status Sync can read and upload raw device status, including device model, battery output, network state, foreground app name, and recent location output. Review the payload preview before enabling automatic sync.

Only upload to a server you control and trust. Avoid exposing raw payloads directly on a public page unless you have filtered sensitive content.

## License

Status Sync Android is licensed under the GNU General Public License v3.0. See [LICENSE](../LICENSE) for details.
