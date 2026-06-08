package com.miafetta.statussync

import android.content.Context
import androidx.work.ListenableWorker
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class StatusUploadResult(
    val success: Boolean,
    val retryable: Boolean,
    val message: String
) {
    fun toWorkResult(): ListenableWorker.Result {
        return when {
            success -> ListenableWorker.Result.success()
            retryable -> ListenableWorker.Result.retry()
            else -> ListenableWorker.Result.failure()
        }
    }
}

object StatusUploader {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    fun upload(context: Context): StatusUploadResult {
        if (!ShizukuShell.hasPermission()) {
            return StatusUploadResult(
                success = false,
                retryable = false,
                message = "Shizuku 未授权或未运行"
            )
        }

        val serverUrl = AppSettings.serverUrl(context)
        if (serverUrl.isBlank()) {
            return StatusUploadResult(
                success = false,
                retryable = false,
                message = "服务器 API 地址为空"
            )
        }

        return try {
            val jsonBody = DeviceStatusCollector.collect().toUploadJson(context)
            val uploadToken = AppSettings.uploadToken(context)
            val requestBuilder = Request.Builder()
                .url(serverUrl)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))

            if (uploadToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $uploadToken")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    StatusUploadResult(
                        success = true,
                        retryable = false,
                        message = "上传成功"
                    )
                } else {
                    StatusUploadResult(
                        success = false,
                        retryable = true,
                        message = "HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            StatusUploadResult(
                success = false,
                retryable = true,
                message = e.javaClass.simpleName
            )
        }
    }
}
