package com.miafetta.statussyncandroid

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import rikka.shizuku.Shizuku

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvPermissionStatus: TextView
    private lateinit var inputServerUrl: TextInputEditText
    private lateinit var inputUploadToken: TextInputEditText
    private lateinit var inputUploadInterval: TextInputEditText
    private lateinit var inputDisplayDelay: TextInputEditText
    private lateinit var switchPrivateMode: SwitchMaterial
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            updatePermissionStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                AppToast.show(this, R.string.toast_permission_granted)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarStyle()
        setContentView(R.layout.activity_settings)
        applyContentInsets()

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        inputServerUrl = findViewById(R.id.inputServerUrl)
        inputUploadToken = findViewById(R.id.inputUploadToken)
        inputUploadInterval = findViewById(R.id.inputUploadInterval)
        inputDisplayDelay = findViewById(R.id.inputDisplayDelay)
        switchPrivateMode = findViewById(R.id.switchPrivateMode)
        val btnRequestPermission = findViewById<Button>(R.id.btnRequestPermission)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        val btnBack = findViewById<Button>(R.id.btnBack)

        updatePermissionStatus()
        inputServerUrl.setText(AppSettings.serverUrl(this))
        inputUploadToken.setText(AppSettings.uploadToken(this))
        inputUploadInterval.setText(AppSettings.uploadIntervalMinutes(this).toString())
        inputDisplayDelay.setText(AppSettings.displayDelayMinutes(this).toString())
        switchPrivateMode.isChecked = AppSettings.privateMode(this)

        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }

        btnBack.setOnClickListener { finish() }
        btnRequestPermission.setOnClickListener { handlePermissionRequest() }
        btnSave.setOnClickListener { saveSettings() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onDestroy() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
        super.onDestroy()
    }

    private fun applyContentInsets() {
        val rootScroll = findViewById<ScrollView>(R.id.settingsRootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootScroll)
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

    private fun saveSettings() {
        val serverUrl = inputServerUrl.text?.toString()?.trim().orEmpty()
        val uploadToken = inputUploadToken.text?.toString()?.trim().orEmpty()
        val intervalText = inputUploadInterval.text?.toString()?.trim().orEmpty()
        val displayDelayText = inputDisplayDelay.text?.toString()?.trim().orEmpty()

        if (!isValidHttpUrl(serverUrl)) {
            inputServerUrl.error = getString(R.string.settings_error_server_url)
            return
        }

        val enteredInterval = intervalText.toLongOrNull()
        if (enteredInterval == null || enteredInterval <= 0) {
            inputUploadInterval.error = getString(R.string.settings_error_upload_interval)
            return
        }

        val displayDelay = displayDelayText.toLongOrNull()
        if (displayDelay == null || displayDelay < 0) {
            inputDisplayDelay.error = getString(R.string.settings_error_display_delay)
            return
        }

        AppSettings.save(
            this,
            serverUrl,
            uploadToken,
            enteredInterval,
            displayDelay,
            switchPrivateMode.isChecked
        )
        inputServerUrl.error = null
        inputUploadToken.error = null
        inputUploadInterval.error = null
        inputDisplayDelay.error = null

        AppToast.show(this, R.string.toast_settings_saved)
    }

    private fun isValidHttpUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        return uri.host?.isNotBlank() == true && uri.scheme in listOf("http", "https")
    }

    private fun handlePermissionRequest() {
        when (getShizukuStatus()) {
            ShizukuStatus.AUTHORIZED -> {
                AppToast.show(this, R.string.toast_permission_already_granted)
            }

            ShizukuStatus.NEEDS_PERMISSION -> requestShizukuPermission()
            ShizukuStatus.UNAVAILABLE -> {
                AppToast.show(this, R.string.toast_shizuku_not_running)
                updatePermissionStatus()
            }
        }
    }

    private fun requestShizukuPermission() {
        runCatching {
            Shizuku.requestPermission(0)
        }.onFailure {
            tvPermissionStatus.setText(R.string.status_shizuku_temporarily_unavailable)
            AppToast.show(this, R.string.toast_request_permission_failed)
        }
    }

    private fun updatePermissionStatus() {
        val statusText = when (getShizukuStatus()) {
            ShizukuStatus.AUTHORIZED -> R.string.status_authorized
            ShizukuStatus.NEEDS_PERMISSION -> R.string.status_needs_permission
            ShizukuStatus.UNAVAILABLE -> R.string.status_unavailable
        }
        tvPermissionStatus.setText(statusText)
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

    private enum class ShizukuStatus {
        AUTHORIZED,
        NEEDS_PERMISSION,
        UNAVAILABLE
    }
}
