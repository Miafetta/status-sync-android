package com.miafetta.statussync

import android.content.Context
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class DeviceStatusSnapshot(
    val model: String,
    val batteryRaw: String,
    val windowRaw: String,
    val wifiRaw: String,
    val networkRaw: String,
    val locationRaw: String,
    val currentAppPackage: String? = null,
    val currentAppName: String? = null
) {
    fun toUploadJson(context: Context): JSONObject {
        val uploadSnapshot = toUploadPreview(context)

        return JSONObject().apply {
            put("model", uploadSnapshot.model)
            put("battery_raw", uploadSnapshot.batteryRaw)
            put("wifi_raw", uploadSnapshot.wifiRaw)
            put("net_raw", uploadSnapshot.networkRaw)
            put("location_raw", uploadSnapshot.locationRaw)
            put("current_app_package", uploadSnapshot.currentAppPackage)
            put("current_app_name", uploadSnapshot.currentAppName)
        }
    }

    fun toUploadPreview(context: Context): DeviceStatusSnapshot {
        if (AppSettings.privateMode(context)) {
            return DeviceStatusSnapshot(
                model = AppSettings.PRIVATE_STATUS_MESSAGE,
                batteryRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
                windowRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
                wifiRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
                networkRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
                locationRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
                currentAppPackage = AppSettings.PRIVATE_STATUS_MESSAGE,
                currentAppName = AppSettings.PRIVATE_STATUS_MESSAGE
            )
        }

        val filteredWindowRaw = filteredWindowRaw(context)
        if (filteredWindowRaw == AppSettings.PRIVATE_STATUS_MESSAGE) {
            return copy(
                windowRaw = filteredWindowRaw,
                currentAppPackage = AppSettings.PRIVATE_STATUS_MESSAGE,
                currentAppName = AppSettings.PRIVATE_STATUS_MESSAGE
            )
        }

        val foregroundPackage = extractForegroundPackage(filteredWindowRaw)
        return copy(
            windowRaw = filteredWindowRaw,
            currentAppPackage = foregroundPackage,
            currentAppName = foregroundPackage?.let { resolveAppLabel(context, it) }
        )
    }

    private fun filteredWindowRaw(context: Context): String {
        val mode = AppSettings.foregroundFilterMode(context)
        if (mode == AppSettings.FOREGROUND_FILTER_NONE) {
            return windowRaw
        }

        val packages = AppSettings.foregroundFilterPackages(context)
        if (packages.isEmpty()) {
            return windowRaw
        }

        val foregroundPackage = extractForegroundPackage(windowRaw)
        val shouldHide = when (mode) {
            AppSettings.FOREGROUND_FILTER_BLACKLIST -> foregroundPackage in packages
            AppSettings.FOREGROUND_FILTER_WHITELIST -> foregroundPackage !in packages
            else -> false
        }

        return if (shouldHide) AppSettings.PRIVATE_STATUS_MESSAGE else windowRaw
    }

    private fun extractForegroundPackage(raw: String): String? {
        return packageNameRegex.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun resolveAppLabel(context: Context, packageName: String): String? {
        return runCatching {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private val packageNameRegex =
            Regex("""\b([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)(?=[/\s}])""")
    }
}

object DeviceStatusCollector {
    fun collect(): DeviceStatusSnapshot {
        return DeviceStatusSnapshot(
            model = runShizukuCommand("getprop ro.product.model"),
            batteryRaw = runShizukuCommand("dumpsys battery"),
            windowRaw = runShizukuCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mFocusedWindow'"),
            wifiRaw = runShizukuCommand("cmd wifi status | grep -E 'Wifi is connected|WifiInfo'"),
            networkRaw = runShizukuCommand("getprop gsm.network.type"),
            locationRaw = runShizukuCommand("dumpsys location | grep -A 1 'last location'")
        )
    }

    private fun runShizukuCommand(command: String): String {
        return try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                ?: return "Shizuku 未连接"
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)

            val finished = process.waitForTimeout(8, TimeUnit.SECONDS.name)
            if (!finished) {
                process.destroy()
                return "读取超时"
            }

            val output = process.getInputStream().readTextFromDescriptor()
            val error = process.getErrorStream().readTextFromDescriptor()
            output.ifBlank { error }.ifBlank { "暂无输出" }
        } catch (e: Exception) {
            "读取失败：${e.javaClass.simpleName}"
        }
    }

    private fun ParcelFileDescriptor.readTextFromDescriptor(): String {
        return ParcelFileDescriptor.AutoCloseInputStream(this).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append('\n')
                }
                output.toString().trim()
            }
        }
    }
}
