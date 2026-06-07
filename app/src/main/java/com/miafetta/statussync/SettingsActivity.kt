package com.miafetta.statussync

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import rikka.shizuku.Shizuku
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvPermissionStatus: TextView
    private lateinit var layoutServerUrl: TextInputLayout
    private lateinit var layoutUploadInterval: TextInputLayout
    private lateinit var layoutDisplayDelay: TextInputLayout
    private lateinit var inputServerUrl: TextInputEditText
    private lateinit var inputUploadToken: TextInputEditText
    private lateinit var inputUploadInterval: TextInputEditText
    private lateinit var inputDisplayDelay: TextInputEditText
    private lateinit var switchPrivateMode: MaterialSwitch
    private lateinit var foregroundFilterSegmentControl: FrameLayout
    private lateinit var foregroundFilterSegmentIndicator: View
    private lateinit var btnForegroundFilterNone: TextView
    private lateinit var btnForegroundFilterBlacklist: TextView
    private lateinit var btnForegroundFilterWhitelist: TextView
    private lateinit var tvForegroundFilterHint: TextView
    private lateinit var foregroundPackageControls: View
    private lateinit var tvForegroundPackages: TextView
    private var currentForegroundFilterMode = AppSettings.FOREGROUND_FILTER_NONE
    private val foregroundPackages = linkedSetOf<String>()
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
        setupTopBarScrollBehavior()

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        layoutServerUrl = findViewById(R.id.layoutServerUrl)
        layoutUploadInterval = findViewById(R.id.layoutUploadInterval)
        layoutDisplayDelay = findViewById(R.id.layoutDisplayDelay)
        inputServerUrl = findViewById(R.id.inputServerUrl)
        inputUploadToken = findViewById(R.id.inputUploadToken)
        inputUploadInterval = findViewById(R.id.inputUploadInterval)
        inputDisplayDelay = findViewById(R.id.inputDisplayDelay)
        switchPrivateMode = findViewById(R.id.switchPrivateMode)
        foregroundFilterSegmentControl = findViewById(R.id.foregroundFilterSegmentControl)
        foregroundFilterSegmentIndicator = findViewById(R.id.foregroundFilterSegmentIndicator)
        btnForegroundFilterNone = findViewById(R.id.btnForegroundFilterNone)
        btnForegroundFilterBlacklist = findViewById(R.id.btnForegroundFilterBlacklist)
        btnForegroundFilterWhitelist = findViewById(R.id.btnForegroundFilterWhitelist)
        tvForegroundFilterHint = findViewById(R.id.tvForegroundFilterHint)
        foregroundPackageControls = findViewById(R.id.foregroundPackageControls)
        tvForegroundPackages = findViewById(R.id.tvForegroundPackages)
        val btnRequestPermission = findViewById<Button>(R.id.btnRequestPermission)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnAddForegroundPackage = findViewById<Button>(R.id.btnAddForegroundPackage)
        val btnClearForegroundPackages = findViewById<Button>(R.id.btnClearForegroundPackages)

        updatePermissionStatus()
        inputServerUrl.setText(AppSettings.serverUrl(this))
        inputUploadToken.setText(AppSettings.uploadToken(this))
        inputUploadInterval.setText(
            String.format(Locale.getDefault(), "%d", AppSettings.uploadIntervalMinutes(this))
        )
        inputDisplayDelay.setText(
            String.format(Locale.getDefault(), "%d", AppSettings.displayDelayMinutes(this))
        )
        switchPrivateMode.isChecked = AppSettings.privateMode(this)
        foregroundPackages.addAll(AppSettings.foregroundFilterPackages(this))
        currentForegroundFilterMode = AppSettings.foregroundFilterMode(this)
        updateForegroundPackagesText()
        updateForegroundFilterUi()
        updateForegroundFilterSegment(false)
        setupAutoSave()

        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }

        btnBack.setOnClickListener { finish() }
        btnRequestPermission.setOnClickListener { handlePermissionRequest() }
        btnAddForegroundPackage.setOnClickListener { showPackagePicker() }
        btnClearForegroundPackages.setOnClickListener {
            foregroundPackages.clear()
            updateForegroundPackagesText()
            saveSettingsIfValid()
        }
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_FOREGROUND_PACKAGES || resultCode != Activity.RESULT_OK) {
            return
        }
        val selectedPackages = data
            ?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGES)
            .orEmpty()
        foregroundPackages.clear()
        foregroundPackages.addAll(selectedPackages.sorted())
        updateForegroundPackagesText()
        saveSettingsIfValid()
    }

    private fun applyContentInsets() {
        val root = findViewById<View>(R.id.settingsRoot)
        val topBar = findViewById<View>(R.id.settingsTopBar)
        val rootScroll = findViewById<ScrollView>(R.id.settingsRootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(
                topBar.paddingLeft,
                systemBars.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            rootScroll.setPadding(
                rootScroll.paddingLeft,
                rootScroll.paddingTop,
                rootScroll.paddingRight,
                0
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupTopBarScrollBehavior() {
        val topBar = findViewById<View>(R.id.settingsTopBar)
        val rootScroll = findViewById<ScrollView>(R.id.settingsRootScroll)
        val topColor = MaterialColors.getColor(
            topBar,
            com.google.android.material.R.attr.colorSurface
        )
        val scrolledColor = MaterialColors.getColor(
            topBar,
            com.google.android.material.R.attr.colorSurfaceContainer
        )

        fun updateTopBar(scrolled: Boolean) {
            topBar.setBackgroundColor(if (scrolled) scrolledColor else topColor)
            topBar.elevation = 0f
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
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
    }

    private fun setupAutoSave() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                saveSettingsIfValid()
            }
        }

        inputServerUrl.addTextChangedListener(watcher)
        inputUploadToken.addTextChangedListener(watcher)
        inputUploadInterval.addTextChangedListener(watcher)
        inputDisplayDelay.addTextChangedListener(watcher)
        switchPrivateMode.setOnCheckedChangeListener { _, _ -> saveSettingsIfValid() }
        btnForegroundFilterNone.setOnClickListener {
            setForegroundFilterMode(AppSettings.FOREGROUND_FILTER_NONE, true)
        }
        btnForegroundFilterBlacklist.setOnClickListener {
            setForegroundFilterMode(AppSettings.FOREGROUND_FILTER_BLACKLIST, true)
        }
        btnForegroundFilterWhitelist.setOnClickListener {
            setForegroundFilterMode(AppSettings.FOREGROUND_FILTER_WHITELIST, true)
        }
    }

    private fun saveSettingsIfValid(): Boolean {
        val serverUrl = inputServerUrl.text?.toString()?.trim().orEmpty()
        val uploadToken = inputUploadToken.text?.toString()?.trim().orEmpty()
        val intervalText = inputUploadInterval.text?.toString()?.trim().orEmpty()
        val displayDelayText = inputDisplayDelay.text?.toString()?.trim().orEmpty()
        clearInputErrors()

        if (!isValidHttpUrl(serverUrl)) {
            layoutServerUrl.error = getString(R.string.settings_error_server_url)
            return false
        }

        val enteredInterval = intervalText.toLongOrNull()
        if (enteredInterval == null || enteredInterval <= 0) {
            layoutUploadInterval.error = getString(R.string.settings_error_upload_interval)
            return false
        }

        val displayDelay = displayDelayText.toLongOrNull()
        if (displayDelay == null || displayDelay < 0) {
            layoutDisplayDelay.error = getString(R.string.settings_error_display_delay)
            return false
        }

        AppSettings.save(
            this,
            serverUrl,
            uploadToken,
            enteredInterval,
            displayDelay,
            switchPrivateMode.isChecked,
            selectedForegroundFilterMode(),
            foregroundPackages
        )
        return true
    }

    private fun clearInputErrors() {
        layoutServerUrl.error = null
        layoutUploadInterval.error = null
        layoutDisplayDelay.error = null
    }

    private fun isValidHttpUrl(value: String): Boolean {
        if (value.isBlank()) {
            return true
        }
        val uri = runCatching { value.toUri() }.getOrNull() ?: return false
        return uri.host?.isNotBlank() == true && uri.scheme in listOf("http", "https")
    }

    private fun selectedForegroundFilterMode(): String {
        return currentForegroundFilterMode
    }

    private fun showPackagePicker() {
        val intent = Intent(this, AppPickerActivity::class.java)
            .putStringArrayListExtra(
                AppPickerActivity.EXTRA_SELECTED_PACKAGES,
                ArrayList(foregroundPackages)
            )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_FOREGROUND_PACKAGES)
    }

    private fun updateForegroundFilterUi() {
        when (currentForegroundFilterMode) {
            AppSettings.FOREGROUND_FILTER_BLACKLIST -> {
                tvForegroundFilterHint.setText(R.string.settings_foreground_filter_blacklist_hint)
                foregroundPackageControls.visibility = View.VISIBLE
            }

            AppSettings.FOREGROUND_FILTER_WHITELIST -> {
                tvForegroundFilterHint.setText(R.string.settings_foreground_filter_whitelist_hint)
                foregroundPackageControls.visibility = View.VISIBLE
            }

            else -> {
                tvForegroundFilterHint.setText(R.string.settings_foreground_filter_none_hint)
                foregroundPackageControls.visibility = View.GONE
            }
        }
    }

    private fun setForegroundFilterMode(mode: String, animated: Boolean) {
        if (currentForegroundFilterMode == mode) {
            return
        }
        currentForegroundFilterMode = mode
        updateForegroundFilterUi()
        updateForegroundFilterSegment(animated)
        saveSettingsIfValid()
    }

    private fun updateForegroundFilterSegment(animated: Boolean) {
        val selectedIndex = when (currentForegroundFilterMode) {
            AppSettings.FOREGROUND_FILTER_BLACKLIST -> 1
            AppSettings.FOREGROUND_FILTER_WHITELIST -> 2
            else -> 0
        }
        val activeTextColor = MaterialColors.getColor(
            foregroundFilterSegmentControl,
            com.google.android.material.R.attr.colorOnPrimary
        )
        val inactiveTextColor = MaterialColors.getColor(
            foregroundFilterSegmentControl,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        listOf(
            btnForegroundFilterNone,
            btnForegroundFilterBlacklist,
            btnForegroundFilterWhitelist
        ).forEachIndexed { index, textView ->
            val targetColor = if (index == selectedIndex) activeTextColor else inactiveTextColor
            if (animated) {
                animateTextColor(textView, textView.currentTextColor, targetColor)
            } else {
                textView.setTextColor(targetColor)
            }
        }

        foregroundFilterSegmentControl.post {
            val availableWidth = foregroundFilterSegmentControl.width -
                foregroundFilterSegmentControl.paddingLeft -
                foregroundFilterSegmentControl.paddingRight
            if (availableWidth <= 0) {
                return@post
            }
            val segmentWidth = availableWidth / 3
            foregroundFilterSegmentIndicator.layoutParams =
                foregroundFilterSegmentIndicator.layoutParams.apply {
                    width = segmentWidth
                }
            val targetTranslation = selectedIndex * segmentWidth.toFloat()
            foregroundFilterSegmentIndicator.animate().cancel()
            if (animated) {
                foregroundFilterSegmentIndicator.animate()
                    .translationX(targetTranslation)
                    .setDuration(FOREGROUND_FILTER_ANIMATION_DURATION_MS)
                    .start()
            } else {
                foregroundFilterSegmentIndicator.translationX = targetTranslation
            }
        }
    }

    private fun animateTextColor(textView: TextView, fromColor: Int, toColor: Int) {
        if (fromColor == toColor) {
            return
        }
        ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = FOREGROUND_FILTER_ANIMATION_DURATION_MS
            addUpdateListener { animator ->
                textView.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun updateForegroundPackagesText() {
        tvForegroundPackages.text = if (foregroundPackages.isEmpty()) {
            getString(R.string.settings_foreground_filter_empty)
        } else {
            foregroundPackages.joinToString("\n\n") { packageName ->
                val label = resolvePackageLabel(packageName)
                if (label.isNullOrBlank()) packageName else "$label\n$packageName"
            }
        }
    }

    private fun resolvePackageLabel(packageName: String): String? {
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            appInfo.loadLabel(packageManager)?.toString()
        }.getOrNull()
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

    companion object {
        private const val REQUEST_PICK_FOREGROUND_PACKAGES = 1001
        private const val FOREGROUND_FILTER_ANIMATION_DURATION_MS = 180L
    }
}
