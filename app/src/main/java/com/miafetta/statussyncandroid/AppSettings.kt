package com.miafetta.statussyncandroid

import android.content.Context

object AppSettings {
    const val DEFAULT_SERVER_URL = "https://api.yourdomain.com/api/upload_raw"
    const val DEFAULT_UPLOAD_TOKEN = ""
    const val DEFAULT_UPLOAD_INTERVAL_MINUTES = 15L
    const val DEFAULT_DISPLAY_DELAY_MINUTES = 0L
    const val PRIVATE_STATUS_MESSAGE = "主人正在摸鱼"

    private const val PREFS_NAME = "status_sync_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_UPLOAD_TOKEN = "upload_token"
    private const val KEY_UPLOAD_INTERVAL = "upload_interval_minutes"
    private const val KEY_DISPLAY_DELAY = "display_delay_minutes"
    private const val KEY_PRIVATE_MODE = "private_mode"

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

    fun save(
        context: Context,
        serverUrl: String,
        uploadToken: String,
        uploadIntervalMinutes: Long,
        displayDelayMinutes: Long,
        privateMode: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_SERVER_URL, serverUrl.trim())
            .putString(KEY_UPLOAD_TOKEN, uploadToken.trim())
            .putLong(KEY_UPLOAD_INTERVAL, uploadIntervalMinutes)
            .putLong(KEY_DISPLAY_DELAY, displayDelayMinutes)
            .putBoolean(KEY_PRIVATE_MODE, privateMode)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
