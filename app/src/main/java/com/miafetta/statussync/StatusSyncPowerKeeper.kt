package com.miafetta.statussync

import android.content.Context

object StatusSyncPowerKeeper {
    fun applyBestEffort(context: Context) {
        if (!ShizukuShell.hasPermission()) {
            return
        }

        val packageName = context.packageName
        val commands = listOf(
            "cmd deviceidle whitelist +$packageName",
            "dumpsys deviceidle whitelist +$packageName",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            "cmd appops set $packageName START_FOREGROUND allow",
            "cmd appops set $packageName WAKE_LOCK allow"
        )

        commands.forEach { command ->
            ShizukuShell.run(command, timeoutSeconds = 5)
        }
    }
}
