package com.miafetta.statussync

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors
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
        val executor = Executors.newFixedThreadPool(statusCommands.size)
        return try {
            val futures = statusCommands.mapValues { (_, command) ->
                executor.submit<String> { ShizukuShell.run(command) }
            }

            DeviceStatusSnapshot(
                model = futures.valueFor("model"),
                batteryRaw = futures.valueFor("battery"),
                windowRaw = futures.valueFor("window"),
                wifiRaw = futures.valueFor("wifi"),
                networkRaw = futures.valueFor("network"),
                locationRaw = futures.valueFor("location")
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun Map<String, java.util.concurrent.Future<String>>.valueFor(key: String): String {
        return runCatching {
            getValue(key).get(COMMAND_RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrElse { error ->
            "读取失败：${error.javaClass.simpleName}"
        }
    }

    private val statusCommands = linkedMapOf(
        "model" to "getprop ro.product.model",
        "battery" to "dumpsys battery",
        "window" to "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mFocusedWindow'",
        "wifi" to "cmd wifi status | grep -E 'Wifi is connected|WifiInfo'",
        "network" to "getprop gsm.network.type",
        "location" to "dumpsys location | grep -A 1 'last location'"
    )

    private const val COMMAND_RESULT_TIMEOUT_SECONDS = 12L
}
