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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

class MainActivity : AppCompatActivity() {
    private lateinit var settingsBottomNav: BottomNavigationView
    private lateinit var connectionSection: View
    private lateinit var runtimeSection: View
    private lateinit var permissionSection: View
    private lateinit var receiveSection: View
    private lateinit var serverBaseInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var roomPasswordInput: EditText
    private lateinit var deviceNameInput: EditText
    private lateinit var clipboardModeGroup: RadioGroup
    private lateinit var clipboardModeForeground: RadioButton
    private lateinit var clipboardModeAccessibility: RadioButton
    private lateinit var clipboardModeShizuku: RadioButton
    private lateinit var autoConnectSwitch: MaterialSwitch
    private lateinit var startOnBootSwitch: MaterialSwitch
    private lateinit var closeAfterStartSwitch: MaterialSwitch
    private lateinit var removeTaskSwitch: MaterialSwitch
    private lateinit var floatingConfirmSwitch: MaterialSwitch
    private lateinit var cacheRetentionInput: EditText
    private lateinit var permissionSummaryText: TextView
    private lateinit var permissionGuideText: TextView
    private lateinit var runtimeAdviceText: TextView
    private lateinit var autoResumeSummaryText: TextView
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView

    private var selectedTabIndex = TAB_CONNECTION
    private var autoResumeAttempted = false

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
        permissionGuideText = findViewById(R.id.permissionGuideText)
        runtimeAdviceText = findViewById(R.id.runtimeAdviceText)
        autoResumeSummaryText = findViewById(R.id.autoResumeSummaryText)
        statusText = findViewById(R.id.statusText)
        lastSyncText = findViewById(R.id.lastSyncText)

        bindBottomNav()
        bindConfig()
        maybeResumeSyncOnLaunch()
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
        maybeResumeSyncOnLaunch()
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
        refreshRuntimeHints()
    }

    private fun maybeResumeSyncOnLaunch() {
        if (autoResumeAttempted || SyncService.isRunning()) {
            return
        }
        val config = SettingsStore.load(this)
        if (!SettingsStore.shouldResumeSync(this)) {
            return
        }
        if (isLoopbackServerBase(config.serverBase)) {
            statusText.text = getString(R.string.status_idle)
            lastSyncText.text = getString(R.string.auto_resume_loopback_hint)
            autoResumeAttempted = true
            return
        }
        autoResumeAttempted = true
        statusText.text = getString(R.string.status_connecting)
        lastSyncText.text = getString(R.string.auto_resume_restored)
        SyncService.start(this)
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
            deviceName = SettingsStore.resolveDeviceNameForSave(this, deviceNameInput.text.toString()),
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
        permissionGuideText.text = buildPermissionGuide(status)
        refreshRuntimeHints()
    }

    private fun refreshRuntimeHints() {
        val config = SettingsStore.load(this)
        val status = PermissionStatusHelper.read(this)
        runtimeAdviceText.text = buildClipboardModeAdvice(config, status)
        autoResumeSummaryText.text = buildAutoResumeSummary(config, status)
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

    private fun buildClipboardModeAdvice(
        config: SettingsStore.Config,
        status: PermissionStatus,
    ): String = when (config.clipboardMode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
            if (status.accessibilityEnabled) {
                "当前是无障碍增强模式：后台文本监听更稳，但会比前台模式更耗电。"
            } else {
                "当前选了无障碍增强模式：推荐开启无障碍后再长期使用，后台复制会更稳，但耗电略高。"
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> "当前选了 Shizuku 模式：请先安装并授权 Shizuku；它能力更强，但重启后通常要重新授权。"
                else -> "当前是 Shizuku 模式：能力更强，适合受系统限制明显的设备，但重启后通常要重新授权。"
            }
        }

        else -> {
            "当前是前台服务模式：最省事、最适合先联调；如果后台复制经常丢失，再切到无障碍或 Shizuku。"
        }
    }

    private fun buildAutoResumeSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
    ): String {
        if (!config.autoConnectEnabled) {
            return "自动续连已关闭：每次都需要你手动点启动同步。"
        }
        if (isLoopbackServerBase(config.serverBase)) {
            return "自动续连暂不可用：当前服务地址还是 127.0.0.1/localhost，请改成 Windows 局域网 IP。"
        }
        if (config.lastDesiredRunningState != SettingsStore.RUNNING_STATE_RUNNING) {
            return "上次是手动停止状态：后续即使重新打开 App，也不会自动恢复同步。"
        }
        val warnings = mutableListOf<String>()
        if (!status.batteryOptimizationIgnored) {
            warnings += "建议忽略电池优化，否则系统可能后台回收同步服务"
        }
        if (config.startOnBootEnabled) {
            warnings += "已开启开机/更新后自动恢复"
        } else {
            warnings += "未开启开机自动恢复，当前仅在你打开 App 时自动续连"
        }
        return "自动续连已就绪：${warnings.joinToString("；")}。"
    }

    private fun buildPermissionGuide(status: PermissionStatus): String {
        val steps = mutableListOf<String>()
        if (!status.notificationsEnabled) {
            steps += "先开通知权限，否则前台同步状态和接收确认提示都不完整。"
        }
        if (!status.batteryOptimizationIgnored) {
            steps += "建议忽略电池优化，尤其是澎湃 / MIUI 一类系统，否则后台容易被杀。"
        }
        if (!status.accessibilityEnabled) {
            steps += "想要更稳的后台文本同步，优先开启无障碍；它更省心，但耗电会略高。"
        }
        if (!status.shizukuInstalled) {
            steps += "如果无障碍场景仍受限，再考虑 Shizuku；能力更强，但重启后通常要重授权。"
        }
        if (!status.overlayEnabled) {
            steps += "想用图片/文件悬浮确认，再补开悬浮窗权限。"
        }
        if (steps.isEmpty()) {
            return "当前常用权限都已到位：通知、悬浮窗、无障碍/电池优化都具备，适合继续做后台稳定性联调。"
        }
        return steps.joinToString("\n")
    }

    companion object {
        private const val TAB_CONNECTION = 0
        private const val TAB_RUNTIME = 1
        private const val TAB_PERMISSIONS = 2
        private const val TAB_RECEIVE = 3
    }
}
