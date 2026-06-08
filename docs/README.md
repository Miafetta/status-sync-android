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

Current package name: `com.miafetta.statussync`. Current version: `1.3.0`.

## Features

- Read device model, battery, network, Wi-Fi, location, and foreground app information through Shizuku.
- Preview the actual upload fields on the main screen.
- Upload once manually or keep syncing on a custom interval through a foreground service.
- Automatic sync uses Shizuku-powered background allowlist commands and a wake lock to reduce background restrictions on heavily customized Android ROMs.
- Status collection runs Shizuku commands concurrently to reduce upload interval drift caused by slow dumpsys commands.
- Configure the server API URL and optional Bearer upload token in the app. The API URL can use `http` or `https`.
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
- On Android 13 or later, notification permission is recommended so the automatic sync foreground service notification can be shown.
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

The app allows `http://` self-hosted endpoints, which is useful for LAN servers or personal APIs without HTTPS. HTTPS is still recommended when uploading sensitive status data.

The app sends a `POST` request with a JSON body:

```http
POST /api/upload_raw
Content-Type: application/json
Authorization: Bearer <upload-token>
```

The upload token is optional. If it is empty, the app does not attach the `Authorization` header. The token is used only as an authentication credential for your server. It does not encrypt the JSON body. If the uploaded status contains private information, use HTTPS and handle access control on your server.

## Automatic Sync

Automatic sync runs as a foreground service. After tapping "Start automatic sync", the app shows a "Status Sync is running" notification and continuously collects and uploads status at the configured interval. The notification action can stop the service.

To reduce background restrictions on heavily customized Android ROMs, automatic sync applies the following best-effort commands through Shizuku:

```text
cmd deviceidle whitelist +com.miafetta.statussync
dumpsys deviceidle whitelist +com.miafetta.statussync
cmd appops set com.miafetta.statussync RUN_ANY_IN_BACKGROUND allow
cmd appops set com.miafetta.statussync RUN_IN_BACKGROUND allow
cmd appops set com.miafetta.statussync START_FOREGROUND allow
cmd appops set com.miafetta.statussync WAKE_LOCK allow
```

The upload interval is scheduled at a fixed rate based on upload start time. Status is collected concurrently before each request is sent. The server records request arrival time, so timing can still drift if a Shizuku status collection or network request takes longer than the configured interval.

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
  ShizukuShell.kt            Shizuku shell command helper
  StatusSyncPowerKeeper.kt   Background allowlist commands for automatic sync
  StatusSyncService.kt       Foreground service automatic sync loop
  StatusSyncApplication.kt   Dynamic color initialization
  StatusUploader.kt          Shared upload logic
  StatusWorker.kt            One-time background worker for manual uploads
```

## Tech Stack

- Kotlin
- Android Jetpack
- Material Components / Material 3
- Shizuku API
- Foreground Service
- WorkManager
- OkHttp

## Permissions

The app declares:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `WAKE_LOCK`
- `QUERY_ALL_PACKAGES`

`QUERY_ALL_PACKAGES` is used to show selectable apps in foreground filtering settings. `POST_NOTIFICATIONS` is used to show the automatic sync foreground service notification. `WAKE_LOCK` reduces the chance of the sync loop being suspended after the screen turns off. System status collection and background allowlist commands depend on Shizuku. The user must start Shizuku and explicitly grant authorization to this app.

## Privacy

Status Sync can read and upload raw device status, including device model, battery output, network state, foreground app name, and recent location output. Review the payload preview before enabling automatic sync.

Only upload to a server you control and trust. Avoid exposing raw payloads directly on a public page unless you have filtered sensitive content.

## Related Projects

```text
Miafetta/status-sync-android  <- current project
        |
        | uploads status
        v
Miafetta/status-sync-api
        |
        | outputs cleaned status JSON
        v
Miafetta/miafetta.github.io
```

- [Miafetta/status-sync-android](https://github.com/Miafetta/status-sync-android): Android status collector and uploader, current project.
- [Miafetta/status-sync-api](https://github.com/Miafetta/status-sync-api): Status data processing API.
- [Miafetta/miafetta.github.io](https://github.com/Miafetta/miafetta.github.io): Blog display frontend.

## License

Status Sync Android is licensed under the GNU General Public License v3.0. See [LICENSE](../LICENSE) for details.
