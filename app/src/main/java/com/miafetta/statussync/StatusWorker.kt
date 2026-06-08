package com.miafetta.statussync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class StatusWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        return StatusUploader.upload(applicationContext).toWorkResult()
    }

    companion object {
        const val UPLOAD_NOW_WORK_NAME = "PhoneStatusUploadNow"
        const val RECURRING_WORK_NAME = "PhoneStatusUpdate"
    }
}
