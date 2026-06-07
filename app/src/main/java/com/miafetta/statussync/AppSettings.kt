package com.miafetta.statussync

import android.content.Context
import androidx.core.content.edit

object AppSettings {
    const val DEFAULT_SERVER_URL = ""
    const val DEFAULT_UPLOAD_TOKEN = ""
    const val DEFAULT_UPLOAD_INTERVAL_MINUTES = 15L
    const val DEFAULT_DISPLAY_DELAY_MINUTES = 0L
    const val PRIVATE_STATUS_MESSAGE = "none"
    const val FOREGROUND_FILTER_NONE = "none"
    const val FOREGROUND_FILTER_BLACKLIST = "blacklist"
    const val FOREGROUND_FILTER_WHITELIST = "whitelist"

    private const val PREFS_NAME = "status_sync_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_UPLOAD_TOKEN = "upload_token"
    private const val KEY_UPLOAD_INTERVAL = "upload_interval_minutes"
    private const val KEY_DISPLAY_DELAY = "display_delay_minutes"
    private const val KEY_PRIVATE_MODE = "private_mode"
    private const val KEY_FOREGROUND_FILTER_MODE = "foreground_filter_mode"
    private const val KEY_FOREGROUND_FILTER_PACKAGES = "foreground_filter_packages"

    fun serverUrl(context: Context): String {
        return prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SERVER_URL
    }

    fun uploadToken(context: Context): String {
        return prefs(context).getString(KEY_UPLOAD_TOKEN, DEFAULT_UPLOAD_TOKEN)
            ?.trim()
            .orEmpty()
    }

    fun uploadIntervalMinutes(context: Context): Long {
        return prefs(context).getLong(
            KEY_UPLOAD_INTERVAL,
            DEFAULT_UPLOAD_INTERVAL_MINUTES
        )
    }

    fun displayDelayMinutes(context: Context): Long {
        return prefs(context).getLong(
            KEY_DISPLAY_DELAY,
            DEFAULT_DISPLAY_DELAY_MINUTES
        )
    }

    fun privateMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PRIVATE_MODE, false)
    }

    fun foregroundFilterMode(context: Context): String {
        return prefs(context).getString(KEY_FOREGROUND_FILTER_MODE, FOREGROUND_FILTER_NONE)
            ?.takeIf { it in foregroundFilterModes }
            ?: FOREGROUND_FILTER_NONE
    }

    fun foregroundFilterPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_FOREGROUND_FILTER_PACKAGES, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSortedSet()
            ?: emptySet()
    }

    fun save(
        context: Context,
        serverUrl: String,
        uploadToken: String,
        uploadIntervalMinutes: Long,
        displayDelayMinutes: Long,
        privateMode: Boolean,
        foregroundFilterMode: String,
        foregroundFilterPackages: Set<String>
    ) {
        prefs(context).edit {
            putString(KEY_SERVER_URL, serverUrl.trim())
            putString(KEY_UPLOAD_TOKEN, uploadToken.trim())
            putLong(KEY_UPLOAD_INTERVAL, uploadIntervalMinutes)
            putLong(KEY_DISPLAY_DELAY, displayDelayMinutes)
            putBoolean(KEY_PRIVATE_MODE, privateMode)
            putString(
                KEY_FOREGROUND_FILTER_MODE,
                foregroundFilterMode.takeIf { it in foregroundFilterModes }
                    ?: FOREGROUND_FILTER_NONE
            )
            putStringSet(
                KEY_FOREGROUND_FILTER_PACKAGES,
                foregroundFilterPackages.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSortedSet()
            )
        }
    }

    private val foregroundFilterModes = setOf(
        FOREGROUND_FILTER_NONE,
        FOREGROUND_FILTER_BLACKLIST,
        FOREGROUND_FILTER_WHITELIST
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
