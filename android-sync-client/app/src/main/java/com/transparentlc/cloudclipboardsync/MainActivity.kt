package com.transparentlc.cloudclipboardsync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

class MainActivity : AppCompatActivity() {
    private lateinit var settingsBottomNav: BottomNavigationView
    private lateinit var connectionSection: LinearLayout
    private lateinit var runtimeSection: LinearLayout
    private lateinit var permissionSection: LinearLayout
    private lateinit var receiveSection: LinearLayout
    private lateinit var serverBaseInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var roomPasswordInput: EditText
    private lateinit var deviceNameInput: EditText
    private lateinit var clipboardModeGroup: RadioGroup
    private lateinit var clipboardModeForeground: RadioButton
    private lateinit var clipboardModeAccessibility: RadioButton
    private lateinit var clipboardModeShizuku: RadioButton
    private lateinit var autoConnectSwitch: Switch
    private lateinit var startOnBootSwitch: Switch
    private lateinit var closeAfterStartSwitch: Switch
    private lateinit var removeTaskSwitch: Switch
    private lateinit var floatingConfirmSwitch: Switch
    private lateinit var cacheRetentionInput: EditText
    private lateinit var permissionSummaryText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView

    private var selectedTabIndex = TAB_CONNECTION

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra(SyncService.EXTRA_STATUS) ?: getString(R.string.status_idle)
            lastSyncText.text = intent?.getStringExtra(SyncService.EXTRA_LAST_RESULT) ?: getString(R.string.last_result_idle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsBottomNav = findViewById(R.id.settingsBottomNav)
        connectionSection = findViewById(R.id.connectionSection)
        runtimeSection = findViewById(R.id.runtimeSection)
        permissionSection = findViewById(R.id.permissionSection)
        receiveSection = findViewById(R.id.receiveSection)
        serverBaseInput = findViewById(R.id.serverBaseInput)
        roomInput = findViewById(R.id.roomInput)
        roomPasswordInput = findViewById(R.id.roomPasswordInput)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        clipboardModeGroup = findViewById(R.id.clipboardModeGroup)
        clipboardModeForeground = findViewById(R.id.clipboardModeForeground)
        clipboardModeAccessibility = findViewById(R.id.clipboardModeAccessibility)
        clipboardModeShizuku = findViewById(R.id.clipboardModeShizuku)
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch)
        startOnBootSwitch = findViewById(R.id.startOnBootSwitch)
        closeAfterStartSwitch = findViewById(R.id.closeAfterStartSwitch)
        removeTaskSwitch = findViewById(R.id.removeTaskSwitch)
        floatingConfirmSwitch = findViewById(R.id.floatingConfirmSwitch)
        cacheRetentionInput = findViewById(R.id.cacheRetentionInput)
        permissionSummaryText = findViewById(R.id.permissionSummaryText)
        statusText = findViewById(R.id.statusText)
        lastSyncText = findViewById(R.id.lastSyncText)

        bindBottomNav()
        bindConfig()
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val config = saveConfig()
            if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
            } else {
                Toast.makeText(this, R.string.config_saved_toast, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            val config = saveConfig()
            if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
                return@setOnClickListener
            }
            SyncService.start(this)
            if (config.removeTaskFromRecents) {
                finishAndRemoveTask()
            } else if (config.closeActivityAfterStart) {
                finish()
            }
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            SyncService.stop(this)
        }
        findViewById<Button>(R.id.openReceivedButton).setOnClickListener {
            startActivity(Intent(this, ReceivedPayloadActivity::class.java))
        }
        findViewById<Button>(R.id.clearCacheButton).setOnClickListener {
            PayloadCacheStore.clearAll(this)
            lastSyncText.text = getString(R.string.cache_cleared_toast)
            Toast.makeText(this, R.string.cache_cleared_toast, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.openNotificationSettingsButton).setOnClickListener {
            openNotificationSettings()
        }
        findViewById<Button>(R.id.openOverlaySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openBatterySettingsButton).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        PayloadCacheStore.pruneExpired(this)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(SyncService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionSummary()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    private fun bindConfig() {
        val config = SettingsStore.load(this)
        serverBaseInput.setText(config.serverBase)
        roomInput.setText(config.room)
        roomPasswordInput.setText(config.roomPassword)
        deviceNameInput.setText(config.deviceName)
        when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> clipboardModeAccessibility.isChecked = true
            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> clipboardModeShizuku.isChecked = true
            else -> clipboardModeForeground.isChecked = true
        }
        autoConnectSwitch.isChecked = config.autoConnectEnabled
        startOnBootSwitch.isChecked = config.startOnBootEnabled
        closeAfterStartSwitch.isChecked = config.closeActivityAfterStart
        removeTaskSwitch.isChecked = config.removeTaskFromRecents
        floatingConfirmSwitch.isChecked = config.floatingEnabled
        cacheRetentionInput.setText(config.cacheRetentionHours.toString())
        statusText.text = getString(R.string.status_idle)
        lastSyncText.text = getString(R.string.last_result_idle)
        refreshPermissionSummary()
    }

    private fun bindBottomNav() {
        settingsBottomNav.setOnItemSelectedListener { item ->
            selectedTabIndex = when (item.itemId) {
                R.id.nav_runtime -> TAB_RUNTIME
                R.id.nav_permissions -> TAB_PERMISSIONS
                R.id.nav_receive -> TAB_RECEIVE
                else -> TAB_CONNECTION
            }
            updateVisibleSection()
            true
        }
        settingsBottomNav.selectedItemId = R.id.nav_connection
        updateVisibleSection()
    }

    private fun updateVisibleSection() {
        connectionSection.visibility = if (selectedTabIndex == TAB_CONNECTION) View.VISIBLE else View.GONE
        runtimeSection.visibility = if (selectedTabIndex == TAB_RUNTIME) View.VISIBLE else View.GONE
        permissionSection.visibility = if (selectedTabIndex == TAB_PERMISSIONS) View.VISIBLE else View.GONE
        receiveSection.visibility = if (selectedTabIndex == TAB_RECEIVE) View.VISIBLE else View.GONE
    }

    private fun saveConfig(): SettingsStore.Config {
        val previous = SettingsStore.load(this)
        val config = SettingsStore.Config(
            serverBase = serverBaseInput.text.toString().trim(),
            room = roomInput.text.toString().trim(),
            roomPassword = roomPasswordInput.text.toString().trim(),
            deviceName = deviceNameInput.text.toString().trim().ifBlank { SettingsStore.detectLocalDeviceName(this) },
            deviceId = previous.deviceId,
            autoConnectEnabled = autoConnectSwitch.isChecked,
            startOnBootEnabled = startOnBootSwitch.isChecked,
            closeActivityAfterStart = closeAfterStartSwitch.isChecked,
            removeTaskFromRecents = removeTaskSwitch.isChecked,
            floatingEnabled = floatingConfirmSwitch.isChecked,
            floatingWidthDp = previous.floatingWidthDp,
            floatingHeightDp = previous.floatingHeightDp,
            floatingPosX = previous.floatingPosX,
            floatingPosY = previous.floatingPosY,
            floatingShowSeconds = previous.floatingShowSeconds,
            cacheRetentionHours = cacheRetentionInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: previous.cacheRetentionHours,
            clipboardMode = selectedClipboardMode(),
            lastDesiredRunningState = previous.lastDesiredRunningState,
        )
        SettingsStore.save(this, config)
        return config
    }

    private fun selectedClipboardMode(): String = when (clipboardModeGroup.checkedRadioButtonId) {
        R.id.clipboardModeAccessibility -> SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY
        R.id.clipboardModeShizuku -> SettingsStore.CLIPBOARD_MODE_SHIZUKU
        else -> SettingsStore.CLIPBOARD_MODE_FOREGROUND
    }

    private fun isLoopbackServerBase(serverBase: String): Boolean {
        if (serverBase.isBlank()) return false
        val host = runCatching { Uri.parse(serverBase).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    private fun showLoopbackHint() {
        val message = getString(R.string.server_base_loopback_hint)
        lastSyncText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun refreshPermissionSummary() {
        val status = PermissionStatusHelper.read(this)
        permissionSummaryText.text = getString(
            R.string.permission_summary_format,
            stateLabel(status.notificationsEnabled),
            stateLabel(status.overlayEnabled),
            stateLabel(status.accessibilityEnabled),
            stateLabel(status.batteryOptimizationIgnored),
            stateLabel(status.shizukuInstalled),
        )
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        startActivity(intent)
    }

    private fun openBatteryOptimizationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        startActivity(intent)
    }

    private fun stateLabel(enabled: Boolean): String = getString(
        if (enabled) R.string.permission_state_enabled else R.string.permission_state_disabled,
    )

    companion object {
        private const val TAB_CONNECTION = 0
        private const val TAB_RUNTIME = 1
        private const val TAB_PERMISSIONS = 2
        private const val TAB_RECEIVE = 3
    }
}
