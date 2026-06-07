package com.miafetta.statussyncandroid

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
    val locationRaw: String
) {
    fun toUploadJson(context: Context): JSONObject {
        val uploadSnapshot = toUploadPreview(context)

        return JSONObject().apply {
            put("model", uploadSnapshot.model)
            put("battery_raw", uploadSnapshot.batteryRaw)
            put("window_raw", uploadSnapshot.windowRaw)
            put("wifi_raw", uploadSnapshot.wifiRaw)
            put("net_raw", uploadSnapshot.networkRaw)
            put("location_raw", uploadSnapshot.locationRaw)
        }
    }

    fun toUploadPreview(context: Context): DeviceStatusSnapshot {
        if (!AppSettings.privateMode(context)) {
            return this
        }

        return DeviceStatusSnapshot(
            model = AppSettings.PRIVATE_STATUS_MESSAGE,
            batteryRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
            windowRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
            wifiRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
            networkRaw = AppSettings.PRIVATE_STATUS_MESSAGE,
            locationRaw = AppSettings.PRIVATE_STATUS_MESSAGE
        )
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
                ?: return "Shizuku 服务未连接"
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)

            val finished = process.waitForTimeout(8, TimeUnit.SECONDS.name)
            if (!finished) {
                process.destroy()
                return "读取超时"
            }

            val output = process.getInputStream().readTextFromDescriptor()
            val error = process.getErrorStream().readTextFromDescriptor()
            output.ifBlank { error }.ifBlank { "无输出" }
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
