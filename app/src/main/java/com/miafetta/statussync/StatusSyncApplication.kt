package com.miafetta.statussync

import android.app.Application
import com.google.android.material.color.DynamicColors

class StatusSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
