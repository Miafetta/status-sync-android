package com.miafetta.statussync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StatusSyncService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSyncService()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_sync_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        acquireWakeLock()

        if (running.compareAndSet(false, true)) {
            executor.execute { runSyncLoop() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        releaseWakeLock()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runSyncLoop() {
        StatusSyncPowerKeeper.applyBestEffort(applicationContext)

        while (running.get()) {
            val startedAt = SystemClock.elapsedRealtime()
            val result = StatusUploader.upload(applicationContext)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val message = if (result.success) {
                getString(R.string.notification_sync_last_success, time)
            } else {
                getString(R.string.notification_sync_last_failed, result.message, time)
            }
            updateNotification(message)
            sleepUntilNextUpload(startedAt)
        }
    }

    private fun sleepUntilNextUpload(uploadStartedAt: Long) {
        val intervalMinutes = AppSettings.uploadIntervalMinutes(applicationContext).coerceAtLeast(1)
        val nextUploadAt = uploadStartedAt + TimeUnit.MINUTES.toMillis(intervalMinutes)
        while (running.get()) {
            val remainingMillis = nextUploadAt - SystemClock.elapsedRealtime()
            if (remainingMillis <= 0) {
                return
            }
            val sleepMillis = minOf(remainingMillis, SLEEP_CHUNK_MILLIS)
            try {
                Thread.sleep(sleepMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                running.set(false)
                return
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:status-sync"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopSyncService() {
        running.set(false)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StatusSyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_schedule_24)
            .setContentTitle(getString(R.string.notification_sync_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_schedule_24, getString(R.string.notification_sync_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_sync_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.miafetta.statussync.action.START_SYNC"
        private const val ACTION_STOP = "com.miafetta.statussync.action.STOP_SYNC"
        private const val NOTIFICATION_CHANNEL_ID = "status_sync_foreground"
        private const val NOTIFICATION_ID = 2001
        private const val SLEEP_CHUNK_MILLIS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, StatusSyncService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
