package com.miafetta.statussync

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShizukuShell {
    fun hasPermission(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun run(command: String, timeoutSeconds: Long = 8): String {
        return try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                ?: return "Shizuku 未连接"
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)

            val finished = process.waitForTimeout(timeoutSeconds, TimeUnit.SECONDS.name)
            if (!finished) {
                process.destroy()
                return "执行超时"
            }

            val output = process.getInputStream().readTextFromDescriptor()
            val error = process.getErrorStream().readTextFromDescriptor()
            output.ifBlank { error }.ifBlank { "无输出" }
        } catch (e: Exception) {
            "执行失败：${e.javaClass.simpleName}"
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
