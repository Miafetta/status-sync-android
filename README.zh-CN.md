# Status Sync Android

<p align="center">
  <img src="design/sync-card-logo-preview.png" width="120" alt="状态同步图标">
</p>

<p align="center">
  <strong>状态同步</strong>
</p>

<p align="center">
  <a href="README.md">English</a> | 简体中文
</p>

状态同步是一个用于个人主页、仪表盘或状态页的 Android 状态上报应用。它可以读取当前手机状态，并将用户允许公开的数据上传到自托管服务器接口，用于展示设备在线状态、网络状态和其他个人状态信息。

应用通过 Shizuku 读取部分系统状态，在主页面展示即将上传的数据，并把 JSON 数据发送到用户在设置中配置的 API 地址。

## 功能

- 通过 Shizuku 读取设备状态。
- 在上传前预览 JSON 数据。
- 支持手动上传一次，也支持按自定义间隔持续上传。
- 支持在应用设置中修改服务器 API 地址。
- 支持配置可选上传密钥，用于 Bearer 认证。
- 支持设置展示延迟，例如延迟 5 分钟后公开新状态。
- 支持不公开模式，开启后公开字段会替换为 `主人正在摸鱼`。
- 支持跟随系统浅色/深色模式，并在可用时使用 Material You 动态颜色。
- 使用接近 Shizuku 层级和设置流程的 Material 风格界面。

## 系统要求

- Android 12 或更高版本。
- 已安装并启动 Shizuku。
- 已在 Shizuku 中授予状态同步权限。
- 一个可以接收 JSON 状态数据的服务器接口。

状态同步不包含后端实现。你需要自行提供 API，并决定如何存储、鉴权和展示上传后的状态数据。

## 数据

应用当前上传以下 JSON 字段：

```json
{
  "model": "设备型号",
  "battery_raw": "电池状态原始输出",
  "window_raw": "当前窗口或前台应用信息",
  "wifi_raw": "Wi-Fi 状态信息",
  "net_raw": "蜂窝网络类型",
  "location_raw": "最近定位记录",
  "private_mode": false,
  "display_delay_minutes": 0,
  "visible_after_unix_ms": 1710000000000
}
```

开启不公开模式后，公开状态字段会替换为：

```text
主人正在摸鱼
```

## 服务器 API

默认上传地址为：

```text
https://api.yourdomain.com/api/upload_raw
```

该地址可以在应用设置中修改。

应用会发送带 JSON 请求体的 `POST` 请求：

```http
POST /api/upload_raw
Content-Type: application/json
Authorization: Bearer <upload-token>
```

上传密钥是可选项。密钥为空时，应用不会添加 `Authorization` 请求头。

上传密钥只用于你的服务器进行身份认证，不会加密 JSON 请求体。如果上传状态中包含隐私信息，请使用 HTTPS，并在服务器侧做好访问控制。

## 隐私

状态同步可以读取并上传原始设备状态，包括设备型号、电池输出、前台窗口信息、网络状态和最近定位输出等。开启自动上传前，请先在应用内检查数据预览。

请仅上传到你自己控制并信任的服务器。除非已经过滤敏感内容，否则不建议把原始 payload 直接展示到公开页面。

## 构建

克隆仓库后使用 Gradle Wrapper 构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 项目结构

```text
app/src/main/java/com/miafetta/statussyncandroid/
  AppSettings.kt             使用 SharedPreferences 保存应用设置
  DeviceStatusCollector.kt   基于 Shizuku 的状态读取逻辑
  MainActivity.kt            主状态页和上传操作页
  SettingsActivity.kt        设置页和 Shizuku 授权页
  StatusWorker.kt            后台上传任务
```

## 技术栈

- Kotlin
- Android Jetpack
- Material Components / Material 3
- Shizuku API
- WorkManager
- OkHttp

## 权限

应用声明以下权限：

- `INTERNET`
- `ACCESS_NETWORK_STATE`

系统状态读取依赖 Shizuku。用户需要主动启动 Shizuku，并明确授予本应用权限。

## 许可证

状态同步使用 GNU General Public License v3.0 开源许可证。详情请查看 [LICENSE](LICENSE)。
