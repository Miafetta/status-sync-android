package com.miafetta.statussyncandroid

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class StatusWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val client = OkHttpClient()

    override fun doWork(): Result {
        val recurring = inputData.getBoolean(KEY_RECURRING, false)
        val result = uploadStatus()
        if (recurring) {
            scheduleNextUpload()
            return Result.success()
        }
        return result
    }

    private fun uploadStatus(): Result {
        if (!hasShizukuPermission()) {
            return Result.failure()
        }

        try {
            val jsonBody = DeviceStatusCollector.collect().toUploadJson(applicationContext)
            val uploadToken = AppSettings.uploadToken(applicationContext)

            val requestBuilder = Request.Builder()
                .url(AppSettings.serverUrl(applicationContext))
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            if (uploadToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $uploadToken")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            return if (response.isSuccessful) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun scheduleNextUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<StatusWorker>()
            .setInitialDelay(AppSettings.uploadIntervalMinutes(applicationContext), TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_RECURRING to true))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            RECURRING_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    private fun hasShizukuPermission(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    companion object {
        const val KEY_RECURRING = "recurring"
        const val RECURRING_WORK_NAME = "PhoneStatusUpdate"
        const val UPLOAD_NOW_WORK_NAME = "PhoneStatusUploadNow"
    }
}
