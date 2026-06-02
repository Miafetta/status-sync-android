package com.miafetta.statussyncandroid

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvModelValue: TextView
    private lateinit var tvBatteryValue: TextView
    private lateinit var tvWindowValue: TextView
    private lateinit var tvWifiValue: TextView
    private lateinit var tvNetworkValue: TextView
    private lateinit var tvLocationValue: TextView
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarStyle()
        setContentView(R.layout.activity_main)
        applyContentInsets()
        setupTopBarScrollBehavior()

        tvStatus = findViewById(R.id.tvStatus)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnUploadNow = findViewById<Button>(R.id.btnUploadNow)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        tvModelValue = findViewById(R.id.tvModelValue)
        tvBatteryValue = findViewById(R.id.tvBatteryValue)
        tvWindowValue = findViewById(R.id.tvWindowValue)
        tvWifiValue = findViewById(R.id.tvWifiValue)
        tvNetworkValue = findViewById(R.id.tvNetworkValue)
        tvLocationValue = findViewById(R.id.tvLocationValue)

        updateStatus()
        setPreviewWaiting()

        if (getShizukuStatus() == ShizukuStatus.AUTHORIZED) {
            loadStatusPreview()
        }

        btnStart.setOnClickListener {
            when (getShizukuStatus()) {
                ShizukuStatus.AUTHORIZED -> {
                    startBackgroundWorker()
                    val interval = AppSettings.uploadIntervalMinutes(this)
                    AppToast.show(
                        this,
                        getString(R.string.toast_sync_started, interval),
                        android.widget.Toast.LENGTH_LONG
                    )
                }

                ShizukuStatus.NEEDS_PERMISSION -> {
                    AppToast.show(this, R.string.toast_request_permission_first)
                }

                ShizukuStatus.UNAVAILABLE -> {
                    AppToast.show(this, R.string.toast_shizuku_not_running)
                    updateStatus()
                }
            }
        }

        btnUploadNow.setOnClickListener {
            when (getShizukuStatus()) {
                ShizukuStatus.AUTHORIZED -> {
                    enqueueImmediateUpload()
                    AppToast.show(this, R.string.toast_upload_once_enqueued)
                }

                ShizukuStatus.NEEDS_PERMISSION -> {
                    AppToast.show(this, R.string.toast_request_permission_first)
                }

                ShizukuStatus.UNAVAILABLE -> {
                    AppToast.show(this, R.string.toast_shizuku_not_running)
                    updateStatus()
                }
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            when (getShizukuStatus()) {
                ShizukuStatus.AUTHORIZED -> loadStatusPreview()
                ShizukuStatus.NEEDS_PERMISSION -> {
                    setPreviewWaiting()
                    AppToast.show(this, R.string.toast_request_permission_first)
                }

                ShizukuStatus.UNAVAILABLE -> {
                    setPreviewWaiting()
                    AppToast.show(this, R.string.toast_shizuku_not_running)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun applyContentInsets() {
        val mainRoot = findViewById<View>(R.id.mainRoot)
        val mainTopBar = findViewById<View>(R.id.mainTopBar)
        val rootScroll = findViewById<ScrollView>(R.id.rootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mainTopBar.setPadding(
                mainTopBar.paddingLeft,
                systemBars.top,
                mainTopBar.paddingRight,
                mainTopBar.paddingBottom
            )
            rootScroll.setPadding(
                rootScroll.paddingLeft,
                rootScroll.paddingTop,
                rootScroll.paddingRight,
                systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(mainRoot)
    }

    private fun setupTopBarScrollBehavior() {
        val mainTopBar = findViewById<View>(R.id.mainTopBar)
        val rootScroll = findViewById<ScrollView>(R.id.rootScroll)
        val topColor = MaterialColors.getColor(
            mainTopBar,
            com.google.android.material.R.attr.colorSurface
        )
        val scrolledColor = MaterialColors.getColor(
            mainTopBar,
            com.google.android.material.R.attr.colorSurfaceContainer
        )

        fun updateTopBar(scrolled: Boolean) {
            mainTopBar.setBackgroundColor(if (scrolled) scrolledColor else topColor)
            mainTopBar.elevation = 0f
        }

        updateTopBar(rootScroll.scrollY > 0)
        rootScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateTopBar(scrollY > 0)
        }
    }

    private fun applySystemBarStyle() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightMode == Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
    }

    private fun updateStatus() {
        val statusText = when (getShizukuStatus()) {
            ShizukuStatus.AUTHORIZED -> R.string.status_authorized
            ShizukuStatus.NEEDS_PERMISSION -> R.string.status_needs_permission
            ShizukuStatus.UNAVAILABLE -> R.string.status_unavailable
        }
        tvStatus.setText(statusText)
    }

    private fun setPreviewWaiting() {
        tvLastUpdated.setText(R.string.preview_waiting)
        listOf(
            tvModelValue,
            tvBatteryValue,
            tvWindowValue,
            tvWifiValue,
            tvNetworkValue,
            tvLocationValue
        ).forEach { it.setText(R.string.preview_waiting) }
    }

    private fun setPreviewLoading() {
        tvLastUpdated.setText(R.string.preview_loading)
        listOf(
            tvModelValue,
            tvBatteryValue,
            tvWindowValue,
            tvWifiValue,
            tvNetworkValue,
            tvLocationValue
        ).forEach { it.setText(R.string.preview_loading) }
    }

    private fun loadStatusPreview() {
        setPreviewLoading()
        btnRefresh.isEnabled = false

        thread {
            val result = runCatching { DeviceStatusCollector.collect() }
            runOnUiThread {
                btnRefresh.isEnabled = true
                result
                    .onSuccess { showStatusPreview(it) }
                    .onFailure {
                        tvLastUpdated.setText(R.string.preview_failed)
                        listOf(
                            tvModelValue,
                            tvBatteryValue,
                            tvWindowValue,
                            tvWifiValue,
                            tvNetworkValue,
                            tvLocationValue
                        ).forEach { view -> view.setText(R.string.preview_failed) }
                    }
            }
        }
    }

    private fun showStatusPreview(snapshot: DeviceStatusSnapshot) {
        val uploadPreview = snapshot.toUploadPreview(this)
        val delayMinutes = AppSettings.displayDelayMinutes(this)
        val updatedText = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date()) + " " + getString(R.string.preview_updated)
        tvLastUpdated.text = if (delayMinutes > 0) {
            updatedText + "，" + getString(R.string.preview_delay_suffix, delayMinutes)
        } else {
            updatedText
        }
        tvModelValue.text = displayValue(uploadPreview.model)
        tvBatteryValue.text = summarizeBattery(uploadPreview.batteryRaw)
        tvWindowValue.text = displayValue(uploadPreview.windowRaw)
        tvWifiValue.text = displayValue(uploadPreview.wifiRaw)
        tvNetworkValue.text = displayValue(uploadPreview.networkRaw)
        tvLocationValue.text = displayValue(uploadPreview.locationRaw)
    }

    private fun summarizeBattery(raw: String): String {
        val status = batteryStatusLabel(raw.valueAfter("status"))
        val level = raw.valueAfter("level")?.let { "$it%" }
        val temperature = raw.valueAfter("temperature")?.toIntOrNull()?.let { "${it / 10.0} C" }
        val powered = listOfNotNull(
            raw.valueAfter("AC powered")?.let { "AC=$it" },
            raw.valueAfter("USB powered")?.let { "USB=$it" },
            raw.valueAfter("Wireless powered")?.let { "无线=$it" }
        ).joinToString(" / ").ifBlank { null }

        return listOfNotNull(
            level?.let { "电量 $it" },
            status?.let { "状态 $it" },
            temperature?.let { "温度 $it" },
            powered?.let { "供电 $it" }
        ).joinToString("\n").ifBlank { displayValue(raw) }
    }

    private fun batteryStatusLabel(statusCode: String?): String? {
        return when (statusCode) {
            "1" -> "未知"
            "2" -> "充电中"
            "3" -> "未充电"
            "4" -> "未充满"
            "5" -> "已充满"
            else -> statusCode
        }
    }

    private fun String.valueAfter(key: String): String? {
        return lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun displayValue(value: String): String {
        return value.trim()
            .ifBlank { "无输出" }
            .let { if (it.length > 360) it.take(360) + "\n..." else it }
    }

    private fun getShizukuStatus(): ShizukuStatus {
        return runCatching {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.UNAVAILABLE
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.AUTHORIZED
            } else {
                ShizukuStatus.NEEDS_PERMISSION
            }
        }.getOrDefault(ShizukuStatus.UNAVAILABLE)
    }

    private fun startBackgroundWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<StatusWorker>()
            .setInputData(workDataOf(StatusWorker.KEY_RECURRING to true))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            StatusWorker.RECURRING_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun enqueueImmediateUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<StatusWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            StatusWorker.UPLOAD_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private enum class ShizukuStatus {
        AUTHORIZED,
        NEEDS_PERMISSION,
        UNAVAILABLE
    }

}
