package com.miafetta.statussync

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AppPickerActivity : AppCompatActivity() {

    private lateinit var inputAppSearch: TextInputEditText
    private lateinit var switchShowSystemApps: MaterialSwitch
    private lateinit var listApps: ListView
    private lateinit var adapter: AppPackageAdapter
    private val selectedPackages = linkedSetOf<String>()
    private var allApps = emptyList<AppPackageItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarStyle()
        setContentView(R.layout.activity_app_picker)
        applyContentInsets()

        inputAppSearch = findViewById(R.id.inputAppSearch)
        switchShowSystemApps = findViewById(R.id.switchShowSystemApps)
        listApps = findViewById(R.id.listApps)
        val btnBack = findViewById<Button>(R.id.btnBack)

        selectedPackages.addAll(
            intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        )
        adapter = AppPackageAdapter()
        listApps.adapter = adapter
        listApps.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            if (item.packageName in selectedPackages) {
                selectedPackages.remove(item.packageName)
            } else {
                selectedPackages.add(item.packageName)
            }
            adapter.notifyDataSetChanged()
        }

        btnBack.setOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult()
                }
            }
        )
        switchShowSystemApps.setOnCheckedChangeListener { _, _ -> updateFilteredApps() }
        inputAppSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateFilteredApps()
            }
        })

        allApps = loadApplications()
        updateFilteredApps()
    }

    private fun applyContentInsets() {
        val root = findViewById<View>(R.id.appPickerRoot)
        val topBar = findViewById<View>(R.id.appPickerTopBar)
        val list = findViewById<ListView>(R.id.listApps)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(
                topBar.paddingLeft,
                systemBars.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            list.setPadding(
                list.paddingLeft,
                list.paddingTop,
                list.paddingRight,
                16.dp
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
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

    private fun loadApplications(): List<AppPackageItem> {
        return getInstalledApplications()
            .mapNotNull { appInfo ->
                val packageName = appInfo.packageName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val label = appInfo.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: packageName
                AppPackageItem(
                    label = label,
                    packageName = packageName,
                    icon = appInfo.loadIcon(packageManager),
                    isSystem = appInfo.isSystemComponent()
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<AppPackageItem> { if (it.packageName in selectedPackages) 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER, AppPackageItem::label)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, AppPackageItem::packageName)
            )
    }

    private fun getInstalledApplications(): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
    }

    private fun updateFilteredApps() {
        val query = inputAppSearch.text?.toString()
            ?.trim()
            ?.lowercase(Locale.getDefault())
            .orEmpty()
        val showSystemApps = switchShowSystemApps.isChecked
        val filteredApps = allApps.filter { item ->
            (showSystemApps || !item.isSystem) &&
                (query.isBlank() ||
                    item.label.lowercase(Locale.getDefault()).contains(query) ||
                    item.packageName.lowercase(Locale.getDefault()).contains(query))
        }
        adapter.submitList(filteredApps)
    }

    private fun finishWithResult() {
        val result = Intent().putStringArrayListExtra(
            EXTRA_SELECTED_PACKAGES,
            ArrayList(selectedPackages.sorted())
        )
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun ApplicationInfo.isSystemComponent(): Boolean {
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return flags and systemFlags != 0
    }

    private data class AppPackageItem(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val isSystem: Boolean
    )

    private inner class AppPackageAdapter : BaseAdapter() {
        private val items = mutableListOf<AppPackageItem>()

        fun submitList(newItems: List<AppPackageItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_package_picker, parent, false)
            val item = getItem(position)
            view.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(item.icon)
            view.findViewById<TextView>(R.id.tvAppLabel).text = item.label
            view.findViewById<TextView>(R.id.tvAppPackage).text = item.packageName
            view.findViewById<MaterialSwitch>(R.id.switchAppPackage).isChecked =
                item.packageName in selectedPackages
            return view
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SELECTED_PACKAGES = "selected_packages"
    }
}
