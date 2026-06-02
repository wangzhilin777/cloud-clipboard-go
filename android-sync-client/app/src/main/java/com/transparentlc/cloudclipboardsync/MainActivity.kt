package com.transparentlc.cloudclipboardsync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

private data class StatusChecklist(
    val blockers: List<String>,
    val suggestions: List<String>,
)

class MainActivity : AppCompatActivity() {
    private lateinit var settingsBottomNav: BottomNavigationView
    private lateinit var homeHeaderCard: View
    private lateinit var connectionSection: View
    private lateinit var runtimeSection: View
    private lateinit var permissionSection: View
    private lateinit var receiveSection: View
    private lateinit var receiveFloatingSettingsGroup: View
    private lateinit var receiveCacheSettingsGroup: View
    private lateinit var receiveFloatingSettingsToggleButton: Button
    private lateinit var receiveCacheSettingsToggleButton: Button
    private lateinit var homeHeaderDetailGroup: View
    private lateinit var homeCollapsedSummaryText: TextView
    private lateinit var homeHeaderToggleButton: Button
    private lateinit var serverBaseInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var roomPasswordInput: EditText
    private lateinit var deviceNameInput: EditText
    private lateinit var clipboardModeGroup: RadioGroup
    private lateinit var clipboardModeForeground: RadioButton
    private lateinit var clipboardModeAccessibility: RadioButton
    private lateinit var clipboardModeShizuku: RadioButton
    private lateinit var autoConnectSwitch: CheckBox
    private lateinit var startOnBootSwitch: CheckBox
    private lateinit var closeAfterStartSwitch: CheckBox
    private lateinit var removeTaskSwitch: CheckBox
    private lateinit var floatingConfirmSwitch: CheckBox
    private lateinit var floatingCompactSwitch: CheckBox
    private lateinit var floatingWidthInput: EditText
    private lateinit var floatingHeightInput: EditText
    private lateinit var floatingShowSecondsInput: EditText
    private lateinit var floatingSnoozeMinutesInput: EditText
    private lateinit var floatingLayoutSummaryText: TextView
    private lateinit var receiveOverlayBadgeText: TextView
    private lateinit var receiveOverlaySummaryText: TextView
    private lateinit var receiveCacheSummaryText: TextView
    private lateinit var cacheRetentionInput: EditText
    private lateinit var permissionSummaryText: TextView
    private lateinit var permissionGuideText: TextView
    private lateinit var runtimeAdviceText: TextView
    private lateinit var runtimeImplementationText: TextView
    private lateinit var autoResumeSummaryText: TextView
    private lateinit var runtimeClipboardDebugText: TextView
    private lateinit var runtimeModeBadgeText: TextView
    private lateinit var permissionOverviewBadgeText: TextView
    private lateinit var runtimeModeActionButton: Button
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView

    private var selectedTabIndex = TAB_CONNECTION
    private var autoResumeAttempted = false
    private var homeHeaderExpanded = false
    private var receiveFloatingSettingsExpanded = false
    private var receiveCacheSettingsExpanded = false
    private var suppressAutoSave = false
    private var latestClipboardRoute = "idle"
    private var latestClipboardDetail = ""

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra(SyncService.EXTRA_STATUS) ?: getString(R.string.status_idle)
            lastSyncText.text = intent?.getStringExtra(SyncService.EXTRA_LAST_RESULT) ?: getString(R.string.last_result_idle)
            latestClipboardRoute = intent?.getStringExtra(SyncService.EXTRA_CLIPBOARD_ROUTE) ?: latestClipboardRoute
            latestClipboardDetail = intent?.getStringExtra(SyncService.EXTRA_CLIPBOARD_DETAIL) ?: latestClipboardDetail
            refreshRuntimeHints()
            updateHomeHeaderSummary()
        }
    }

    private val payloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshRuntimeHints()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsBottomNav = findViewById(R.id.settingsBottomNav)
        homeHeaderCard = findViewById(R.id.homeHeaderCard)
        connectionSection = findViewById(R.id.connectionSection)
        runtimeSection = findViewById(R.id.runtimeSection)
        permissionSection = findViewById(R.id.permissionSection)
        receiveSection = findViewById(R.id.receiveSection)
        receiveFloatingSettingsGroup = findViewById(R.id.receiveFloatingSettingsGroup)
        receiveCacheSettingsGroup = findViewById(R.id.receiveCacheSettingsGroup)
        receiveFloatingSettingsToggleButton = findViewById(R.id.receiveFloatingSettingsToggleButton)
        receiveCacheSettingsToggleButton = findViewById(R.id.receiveCacheSettingsToggleButton)
        homeHeaderDetailGroup = findViewById(R.id.homeHeaderDetailGroup)
        homeCollapsedSummaryText = findViewById(R.id.homeCollapsedSummaryText)
        homeHeaderToggleButton = findViewById(R.id.homeHeaderToggleButton)
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
        floatingCompactSwitch = findViewById(R.id.floatingCompactSwitch)
        floatingWidthInput = findViewById(R.id.floatingWidthInput)
        floatingHeightInput = findViewById(R.id.floatingHeightInput)
        floatingShowSecondsInput = findViewById(R.id.floatingShowSecondsInput)
        floatingSnoozeMinutesInput = findViewById(R.id.floatingSnoozeMinutesInput)
        floatingLayoutSummaryText = findViewById(R.id.floatingLayoutSummaryText)
        receiveOverlayBadgeText = findViewById(R.id.receiveOverlayBadgeText)
        receiveOverlaySummaryText = findViewById(R.id.receiveOverlaySummaryText)
        receiveCacheSummaryText = findViewById(R.id.receiveCacheSummaryText)
        cacheRetentionInput = findViewById(R.id.cacheRetentionInput)
        permissionSummaryText = findViewById(R.id.permissionSummaryText)
        permissionGuideText = findViewById(R.id.permissionGuideText)
        runtimeAdviceText = findViewById(R.id.runtimeAdviceText)
        runtimeImplementationText = findViewById(R.id.runtimeImplementationText)
        autoResumeSummaryText = findViewById(R.id.autoResumeSummaryText)
        runtimeClipboardDebugText = findViewById(R.id.runtimeClipboardDebugText)
        runtimeModeBadgeText = findViewById(R.id.runtimeModeBadgeText)
        permissionOverviewBadgeText = findViewById(R.id.permissionOverviewBadgeText)
        runtimeModeActionButton = findViewById(R.id.runtimeModeActionButton)
        statusText = findViewById(R.id.statusText)
        lastSyncText = findViewById(R.id.lastSyncText)

        bindBottomNav()
        bindHomeHeader()
        bindConfig()
        bindReceiveSection()
        maybeResumeSyncOnLaunch()
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val config = saveConfig()
            if (config.serverBase.isBlank()) {
                showMissingServerBaseHint()
            } else if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
            } else {
                Toast.makeText(this, R.string.config_saved_toast, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            val config = saveConfig()
            if (config.serverBase.isBlank()) {
                showMissingServerBaseHint()
                return@setOnClickListener
            }
            if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
                return@setOnClickListener
            }
            val validation = RuntimeModeValidator.validate(this, config)
            if (!validation.ready) {
                statusText.text = getString(R.string.status_idle)
                lastSyncText.text = validation.message
                Toast.makeText(this, validation.message, Toast.LENGTH_LONG).show()
                selectedTabIndex = TAB_RUNTIME
                settingsBottomNav.selectedItemId = R.id.nav_runtime
                updateVisibleSection()
                openRuntimeModeAction(validation.action)
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
            openReceivedActivity(Intent(this, ReceivedPayloadActivity::class.java))
        }
        findViewById<Button>(R.id.openPendingReceivedButton).setOnClickListener {
            openReceivedActivity(ReceivedPayloadActivity.createIntent(this, ReceivedPayloadActivity.FilterMode.PENDING))
        }
        findViewById<Button>(R.id.openProcessedReceivedButton).setOnClickListener {
            openReceivedActivity(ReceivedPayloadActivity.createIntent(this, ReceivedPayloadActivity.FilterMode.PROCESSED))
        }
        findViewById<Button>(R.id.openSnoozedReceivedButton).setOnClickListener {
            openReceivedActivity(ReceivedPayloadActivity.createIntent(this, ReceivedPayloadActivity.FilterMode.SNOOZED))
        }
        findViewById<Button>(R.id.clearCacheButton).setOnClickListener {
            PayloadCacheStore.clearAll(this)
            lastSyncText.text = getString(R.string.cache_cleared_toast)
            refreshRuntimeHints()
            Toast.makeText(this, R.string.cache_cleared_toast, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.clearProcessedCacheButton).setOnClickListener {
            val removed = PayloadCacheStore.clearProcessed(this)
            if (removed <= 0) {
                Toast.makeText(this, R.string.payload_clear_processed_empty_toast, Toast.LENGTH_SHORT).show()
            } else {
                sendBroadcast(Intent(SyncService.ACTION_PAYLOAD_UPDATED).apply { setPackage(packageName) })
                lastSyncText.text = getString(R.string.payload_clear_processed_toast, removed)
                refreshRuntimeHints()
                Toast.makeText(this, getString(R.string.payload_clear_processed_toast, removed), Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.restoreSnoozedButton).setOnClickListener {
            val restored = PayloadCacheStore.clearSnoozed(this)
            if (restored <= 0) {
                Toast.makeText(this, R.string.payload_restore_snoozed_empty_toast, Toast.LENGTH_SHORT).show()
            } else {
                sendBroadcast(Intent(SyncService.ACTION_PAYLOAD_UPDATED).apply { setPackage(packageName) })
                lastSyncText.text = getString(R.string.payload_restore_snoozed_toast, restored)
                refreshRuntimeHints()
                Toast.makeText(this, getString(R.string.payload_restore_snoozed_toast, restored), Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.resetFloatingPositionButton).setOnClickListener {
            saveReceiveSettings(refreshAfter = false)
            SettingsStore.resetFloatingPosition(this)
            refreshRuntimeHints()
            Toast.makeText(this, R.string.floating_position_reset_toast, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.previewFloatingConfirmButton).setOnClickListener {
            saveReceiveSettings(refreshAfter = false)
            if (!PermissionStatusHelper.read(this).overlayEnabled) {
                Toast.makeText(this, R.string.preview_floating_confirm_permission_toast, Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@setOnClickListener
            }
            FloatingConfirmService.showPreview(this)
        }
        findViewById<Button>(R.id.openNotificationSettingsButton).setOnClickListener {
            openNotificationSettings()
        }
        findViewById<Button>(R.id.openOverlaySettingsButton).setOnClickListener {
            openOverlaySettings()
        }
        findViewById<Button>(R.id.openReceiveOverlaySettingsButton).setOnClickListener {
            saveReceiveSettings(refreshAfter = false)
            openOverlaySettings()
        }
        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openBatterySettingsButton).setOnClickListener {
            openBatteryOptimizationSettings()
        }
        findViewById<Button>(R.id.openVendorBackgroundSettingsButton).setOnClickListener {
            openVendorBackgroundSettings()
        }
        runtimeModeActionButton.setOnClickListener {
            handleRuntimeModeQuickAction()
        }
        clipboardModeGroup.setOnCheckedChangeListener { _, _ ->
            refreshRuntimeHints()
        }
        autoConnectSwitch.setOnCheckedChangeListener { _, _ -> refreshRuntimeHints() }
        startOnBootSwitch.setOnCheckedChangeListener { _, _ -> refreshRuntimeHints() }
        floatingConfirmSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) saveReceiveSettings()
        }
        floatingCompactSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) saveReceiveSettings()
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
        ContextCompat.registerReceiver(
            this,
            payloadUpdateReceiver,
            IntentFilter(SyncService.ACTION_PAYLOAD_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        saveReceiveSettings(refreshAfter = false)
        refreshPermissionSummary()
        maybeResumeSyncOnLaunch()
    }

    override fun onPause() {
        saveReceiveSettings(refreshAfter = false)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
        unregisterReceiver(payloadUpdateReceiver)
    }

    private fun bindHomeHeader() {
        homeHeaderToggleButton.setOnClickListener {
            homeHeaderExpanded = !homeHeaderExpanded
            updateHomeHeaderSummary()
        }
        updateHomeHeaderSummary()
    }

    private fun bindReceiveSection() {
        receiveFloatingSettingsToggleButton.setOnClickListener {
            receiveFloatingSettingsExpanded = !receiveFloatingSettingsExpanded
            syncReceiveSectionToggles()
        }
        receiveCacheSettingsToggleButton.setOnClickListener {
            receiveCacheSettingsExpanded = !receiveCacheSettingsExpanded
            syncReceiveSectionToggles()
        }
        listOf(
            floatingWidthInput,
            floatingHeightInput,
            floatingShowSecondsInput,
            floatingSnoozeMinutesInput,
            cacheRetentionInput,
        ).forEach { input ->
            input.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveReceiveSettings(refreshAfter = true)
                }
            }
            input.doAfterTextChanged {
                if (!suppressAutoSave) {
                    refreshFloatingDraftSummary()
                }
            }
        }
        syncReceiveSectionToggles()
        refreshFloatingDraftSummary()
    }

    private fun bindConfig() {
        suppressAutoSave = true
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
        floatingCompactSwitch.isChecked = config.floatingCompactEnabled
        floatingWidthInput.setText(config.floatingWidthDp.toString())
        floatingHeightInput.setText(config.floatingHeightDp.toString())
        floatingShowSecondsInput.setText(config.floatingShowSeconds.toString())
        floatingSnoozeMinutesInput.setText(config.floatingSnoozeMinutes.toString())
        cacheRetentionInput.setText(config.cacheRetentionHours.toString())
        statusText.text = getString(R.string.status_idle)
        lastSyncText.text = getString(R.string.last_result_idle)
        suppressAutoSave = false
        refreshPermissionSummary()
        refreshRuntimeHints()
        refreshFloatingDraftSummary()
        updateHomeHeaderSummary()
    }

    private fun maybeResumeSyncOnLaunch() {
        if (autoResumeAttempted || SyncService.isRunning()) {
            return
        }
        val config = SettingsStore.load(this)
        if (!SettingsStore.shouldResumeSync(this)) {
            return
        }
        if (config.serverBase.isBlank()) {
            statusText.text = getString(R.string.status_idle)
            lastSyncText.text = getString(R.string.auto_resume_missing_server_hint)
            autoResumeAttempted = true
            return
        }
        if (isLoopbackServerBase(config.serverBase)) {
            statusText.text = getString(R.string.status_idle)
            lastSyncText.text = getString(R.string.auto_resume_loopback_hint)
            autoResumeAttempted = true
            return
        }
        autoResumeAttempted = true
        val validation = RuntimeModeValidator.validate(this, config)
        if (!validation.ready) {
            statusText.text = getString(R.string.status_idle)
            lastSyncText.text = validation.message
            return
        }
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
        homeHeaderCard.visibility = if (selectedTabIndex == TAB_CONNECTION) View.VISIBLE else View.GONE
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
            floatingCompactEnabled = floatingCompactSwitch.isChecked,
            floatingWidthDp = floatingWidthInput.text.toString().toIntOrNull()?.coerceIn(240, 420) ?: previous.floatingWidthDp,
            floatingHeightDp = floatingHeightInput.text.toString().toIntOrNull()?.coerceIn(100, 240) ?: previous.floatingHeightDp,
            floatingPosX = previous.floatingPosX,
            floatingPosY = previous.floatingPosY,
            floatingShowSeconds = floatingShowSecondsInput.text.toString().toIntOrNull()?.coerceIn(5, 60) ?: previous.floatingShowSeconds,
            floatingSnoozeMinutes = floatingSnoozeMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: previous.floatingSnoozeMinutes,
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

    private fun saveReceiveSettings(refreshAfter: Boolean = true) {
        if (suppressAutoSave) {
            return
        }
        val previous = SettingsStore.load(this)
        val updated = previous.copy(
            floatingEnabled = floatingConfirmSwitch.isChecked,
            floatingCompactEnabled = floatingCompactSwitch.isChecked,
            floatingWidthDp = floatingWidthInput.text.toString().toIntOrNull()?.coerceIn(240, 420) ?: previous.floatingWidthDp,
            floatingHeightDp = floatingHeightInput.text.toString().toIntOrNull()?.coerceIn(100, 240) ?: previous.floatingHeightDp,
            floatingShowSeconds = floatingShowSecondsInput.text.toString().toIntOrNull()?.coerceIn(5, 60) ?: previous.floatingShowSeconds,
            floatingSnoozeMinutes = floatingSnoozeMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: previous.floatingSnoozeMinutes,
            cacheRetentionHours = cacheRetentionInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: previous.cacheRetentionHours,
        )
        SettingsStore.save(this, updated)
        if (refreshAfter) {
            refreshRuntimeHints()
        } else {
            refreshFloatingDraftSummary()
        }
    }

    private fun isLoopbackServerBase(serverBase: String): Boolean {
        return SettingsStore.isLoopbackServerBase(serverBase)
    }

    private fun showLoopbackHint() {
        val message = getString(R.string.server_base_loopback_hint)
        lastSyncText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showMissingServerBaseHint() {
        val message = getString(R.string.server_base_missing_hint)
        lastSyncText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun refreshPermissionSummary() {
        val status = PermissionStatusHelper.read(this)
        val config = SettingsStore.load(this)
        val checklist = buildPermissionChecklist(config, status)
        bindStatusBadge(
            permissionOverviewBadgeText,
            ready = checklist.blockers.isEmpty(),
            readyText = getString(R.string.permission_overview_ready),
            warningText = getString(R.string.permission_overview_blocked),
        )
        permissionSummaryText.text = buildPermissionSummary(status, checklist)
        permissionGuideText.text = buildPermissionGuide(checklist)
        refreshRuntimeHints()
    }

    private fun refreshRuntimeHints() {
        val config = SettingsStore.load(this)
        val status = PermissionStatusHelper.read(this)
        val validation = RuntimeModeValidator.validate(this, config)
        val support = ClipboardModeSupportHelper.describe(this, config.clipboardMode, status)
        bindStatusBadge(
            runtimeModeBadgeText,
            ready = validation.ready,
            readyText = getString(R.string.runtime_recommendation_ready),
            warningText = getString(R.string.runtime_recommendation_blocked),
        )
        runtimeAdviceText.text = buildClipboardModeAdvice(config, status, validation)
        runtimeImplementationText.text = buildRuntimeImplementationSummary(config, status, support)
        runtimeModeActionButton.text = runtimeModeActionLabel(config, status)
        autoResumeSummaryText.text = buildAutoResumeSummary(config, status)
        runtimeClipboardDebugText.text = buildClipboardDebugSummary(config, status, validation)
        floatingLayoutSummaryText.text = getString(
            R.string.floating_layout_summary_format,
            config.floatingPosX,
            config.floatingPosY,
            config.floatingWidthDp,
            config.floatingHeightDp,
            config.floatingShowSeconds,
            config.floatingSnoozeMinutes,
        ) + "\n" + getString(
            if (config.floatingCompactEnabled) {
                R.string.floating_layout_compact_on
            } else {
                R.string.floating_layout_compact_off
            },
        )
        val overlayReady = config.floatingEnabled && status.overlayEnabled
        bindStatusBadge(
            receiveOverlayBadgeText,
            ready = overlayReady,
            readyText = getString(R.string.receive_overlay_ready),
            warningText = getString(R.string.receive_overlay_attention),
        )
        receiveOverlaySummaryText.text = when {
            !config.floatingEnabled -> getString(R.string.receive_overlay_disabled_summary)
            !status.overlayEnabled -> getString(R.string.receive_overlay_permission_summary)
            config.floatingCompactEnabled -> getString(R.string.receive_overlay_ready_summary) + "\n当前已启用紧凑卡片，会优先显示标题和操作按钮。"
            else -> getString(R.string.receive_overlay_ready_summary) + "\n当前使用详细卡片，会显示来源、大小和操作提示。"
        }
        receiveCacheSummaryText.text = buildReceiveCacheSummary()
        refreshFloatingDraftSummary()
        updateHomeHeaderSummary()
    }

    private fun refreshFloatingDraftSummary() {
        val stored = SettingsStore.load(this)
        val width = floatingWidthInput.text.toString().toIntOrNull()?.coerceIn(240, 420) ?: stored.floatingWidthDp
        val height = floatingHeightInput.text.toString().toIntOrNull()?.coerceIn(100, 240) ?: stored.floatingHeightDp
        val showSeconds = floatingShowSecondsInput.text.toString().toIntOrNull()?.coerceIn(5, 60) ?: stored.floatingShowSeconds
        val snoozeMinutes = floatingSnoozeMinutesInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: stored.floatingSnoozeMinutes
        val compactLabel = getString(
            if (floatingCompactSwitch.isChecked) R.string.floating_layout_compact_on else R.string.floating_layout_compact_off,
        )
        floatingLayoutSummaryText.text = getString(
            R.string.floating_layout_summary_format,
            stored.floatingPosX,
            stored.floatingPosY,
            width,
            height,
            showSeconds,
            snoozeMinutes,
        ) + "\n" + compactLabel
    }

    private fun syncReceiveSectionToggles() {
        receiveFloatingSettingsGroup.visibility = if (receiveFloatingSettingsExpanded) View.VISIBLE else View.GONE
        receiveCacheSettingsGroup.visibility = if (receiveCacheSettingsExpanded) View.VISIBLE else View.GONE
        receiveFloatingSettingsToggleButton.text = getString(
            if (receiveFloatingSettingsExpanded) {
                R.string.receive_section_collapse_floating
            } else {
                R.string.receive_section_expand_floating
            },
        )
        receiveCacheSettingsToggleButton.text = getString(
            if (receiveCacheSettingsExpanded) {
                R.string.receive_section_collapse_cache
            } else {
                R.string.receive_section_expand_cache
            },
        )
    }

    private fun updateHomeHeaderSummary() {
        val config = SettingsStore.load(this)
        val compactStatus = statusText.text?.toString()?.trim().orEmpty().ifBlank { getString(R.string.status_idle) }
        val roomLabel = config.room.ifBlank { getString(R.string.default_room_label) }
        val receiveMode = if (config.floatingEnabled) {
            getString(R.string.home_receive_mode_floating)
        } else {
            getString(R.string.home_receive_mode_notification)
        }
        homeCollapsedSummaryText.text = getString(R.string.home_collapsed_summary_format, compactStatus, roomLabel, receiveMode)
        homeCollapsedSummaryText.visibility = if (homeHeaderExpanded) View.GONE else View.VISIBLE
        homeHeaderDetailGroup.visibility = if (homeHeaderExpanded) View.VISIBLE else View.GONE
        homeHeaderToggleButton.text = getString(
            if (homeHeaderExpanded) R.string.home_collapse_button else R.string.home_expand_button,
        )
    }

    private fun openReceivedActivity(intent: Intent) {
        runCatching {
            startActivity(intent)
        }.onFailure { error ->
            lastSyncText.text = error.message ?: getString(R.string.received_page_open_failed)
            Toast.makeText(this, R.string.received_page_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleRuntimeModeQuickAction() {
        val config = SettingsStore.load(this)
        val status = PermissionStatusHelper.read(this)
        when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
                when {
                    !status.accessibilityEnabled -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    !status.batteryOptimizationIgnored -> openBatteryOptimizationSettings()
                    shouldSuggestVendorBackgroundSettings() -> openVendorBackgroundSettings()
                    !status.notificationsEnabled -> openNotificationSettings()
                    else -> Toast.makeText(this, R.string.runtime_mode_action_accessibility_ready_toast, Toast.LENGTH_LONG).show()
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                if (!status.shizukuInstalled) {
                    openShizuku(false)
                } else if (!status.notificationsEnabled) {
                    openNotificationSettings()
                } else {
                    openShizuku(true)
                }
            }

            else -> {
                when {
                    !status.notificationsEnabled -> openNotificationSettings()
                    !status.batteryOptimizationIgnored -> openBatteryOptimizationSettings()
                    shouldSuggestVendorBackgroundSettings() -> openVendorBackgroundSettings()
                    else -> Toast.makeText(this, R.string.runtime_mode_action_foreground_ready_toast, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openRuntimeModeAction(action: RuntimeModeAction) {
        when (action) {
            RuntimeModeAction.OPEN_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            RuntimeModeAction.OPEN_SHIZUKU -> {
                openShizuku(PermissionStatusHelper.read(this).shizukuInstalled)
            }

            RuntimeModeAction.NONE -> Unit
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        startActivity(intent)
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openBatteryOptimizationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        startActivity(intent)
    }

    private fun openVendorBackgroundSettings() {
        val intents = buildVendorBackgroundIntents()
        intents.firstOrNull { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
        }?.let {
            Toast.makeText(this, R.string.vendor_background_settings_toast, Toast.LENGTH_LONG).show()
            return
        }

        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching {
            startActivity(fallback)
            Toast.makeText(this, R.string.vendor_background_settings_missing_toast, Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, R.string.vendor_background_settings_missing_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildVendorBackgroundIntents(): List<Intent> {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val packageUri = Uri.parse("package:$packageName")

        fun componentIntent(packageName: String, className: String): Intent =
            Intent().apply {
                component = ComponentName(packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", this@MainActivity.packageName)
                putExtra("packageName", this@MainActivity.packageName)
                data = packageUri
            }

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                listOf(
                    componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    componentIntent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                )

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                listOf(
                    componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                )

            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                listOf(
                    componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    componentIntent("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"),
                )

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                listOf(
                    componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                )

            manufacturer.contains("samsung") ->
                listOf(
                    componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                )

            else -> emptyList()
        }
    }

    private fun openShizuku(installed: Boolean) {
        if (installed) {
            val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                startActivity(launchIntent)
                return
            }
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:moe.shizuku.privileged.api"),
        )
        try {
            startActivity(fallbackIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.runtime_mode_action_shizuku_missing_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun runtimeModeActionLabel(
        config: SettingsStore.Config,
        status: PermissionStatus,
    ): String = when (config.clipboardMode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> when {
            !status.accessibilityEnabled -> getString(R.string.runtime_mode_action_accessibility)
            !status.batteryOptimizationIgnored -> getString(R.string.runtime_mode_action_battery)
            shouldSuggestVendorBackgroundSettings() -> getString(R.string.open_vendor_background_settings_button)
            !status.notificationsEnabled -> getString(R.string.open_notification_settings_button)
            else -> getString(R.string.runtime_mode_action_accessibility_ready)
        }
        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> getString(
            if (!status.shizukuInstalled) {
                R.string.runtime_mode_action_shizuku_install
            } else if (!status.notificationsEnabled) {
                R.string.open_notification_settings_button
            } else {
                R.string.runtime_mode_action_shizuku
            },
        )

        else -> when {
            !status.notificationsEnabled -> getString(R.string.open_notification_settings_button)
            !status.batteryOptimizationIgnored -> getString(R.string.runtime_mode_action_battery)
            shouldSuggestVendorBackgroundSettings() -> getString(R.string.open_vendor_background_settings_button)
            else -> getString(R.string.runtime_mode_action_foreground_ready)
        }
    }

    private fun buildRuntimeImplementationSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
        support: ClipboardModeSupport,
    ): String {
        val readyItems = mutableListOf<String>()
        val pendingItems = mutableListOf<String>()

        when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
                if (status.accessibilityEnabled) {
                    readyItems += "无障碍服务已开启"
                } else {
                    pendingItems += "需要先开启无障碍服务"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                if (status.shizukuInstalled) {
                    readyItems += "已检测到 Shizuku 环境"
                    pendingItems += "独立增强链路仍在接入，当前阶段暂不开放启动"
                } else {
                    pendingItems += "需要先安装或拉起 Shizuku"
                }
            }

            else -> {
                readyItems += "前台服务主链路已可用"
            }
        }

        if (status.notificationsEnabled) {
            readyItems += "通知权限已开启"
        } else {
            pendingItems += "建议开启通知，避免前台服务状态和接收提醒不完整"
        }
        if (status.batteryOptimizationIgnored) {
            readyItems += "已忽略电池优化"
        } else {
            pendingItems += "建议忽略电池优化，降低后台被系统回收的概率"
        }
        if (shouldSuggestVendorBackgroundSettings()) {
            pendingItems += "建议再检查厂商后台保活设置"
        }

        val nextAction = runtimeModeActionLabel(config, status)
        val readyLine = if (readyItems.isEmpty()) "当前已就绪：暂无" else "当前已就绪：${readyItems.joinToString("；")}" 
        val pendingLine = if (pendingItems.isEmpty()) "当前待补齐：暂无" else "当前待补齐：${pendingItems.joinToString("；")}" 
        return buildString {
            appendLine(support.implementationSummary)
            appendLine()
            appendLine(readyLine)
            appendLine(pendingLine)
            append("快捷处理：下方按钮会优先带你去“")
            append(nextAction)
            append("”。")
        }
    }

    private fun shouldSuggestVendorBackgroundSettings(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
            manufacturer.contains("huawei") || manufacturer.contains("honor") ||
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ||
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
            manufacturer.contains("samsung")
    }

    private fun stateLabel(enabled: Boolean): String = getString(
        if (enabled) R.string.permission_state_enabled else R.string.permission_state_disabled,
    )

    private fun bindStatusBadge(
        view: TextView,
        ready: Boolean,
        readyText: String,
        warningText: String,
    ) {
        @DrawableRes val backgroundRes = if (ready) {
            R.drawable.status_chip_ready_background
        } else {
            R.drawable.status_chip_warning_background
        }
        @ColorRes val textColorRes = if (ready) {
            R.color.cc_success
        } else {
            R.color.cc_warning
        }
        view.setBackgroundResource(backgroundRes)
        view.setTextColor(ContextCompat.getColor(this, textColorRes))
        view.text = if (ready) readyText else warningText
    }

    private fun buildClipboardModeAdvice(
        config: SettingsStore.Config,
        status: PermissionStatus,
        validation: RuntimeModeValidation,
    ): String = when (config.clipboardMode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
            if (status.accessibilityEnabled) {
                "当前模式：无障碍增强\n启动状态：可直接启动同步\n系统限制：${clipboardRestrictionSummary()}\n说明：除了系统剪贴板回调，还会在界面交互时主动触发补检查，后台文本监听会更稳，但会比前台模式更耗电。"
            } else {
                "当前模式：无障碍增强\n启动状态：暂时被拦截\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：开启后后台复制会更稳，但耗电略高。"
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> "当前模式：Shizuku\n启动状态：当前版本暂不开放启动\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：后续接入真实增强链路后，再开放给受系统限制明显的设备使用。"
                else -> "当前模式：Shizuku\n启动状态：当前版本暂不开放启动\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：当前已探测到 Shizuku 环境，但独立增强链路仍在接入中。"
            }
        }

        else -> {
            val batteryLine = if (status.batteryOptimizationIgnored) {
                "电池策略：已忽略电池优化，后台稳定性更好。"
            } else {
                "电池策略：建议补开忽略电池优化，否则系统可能后台回收同步服务。"
            }
            val notificationLine = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notificationsEnabled) {
                "通知策略：当前还没允许通知，前台服务状态和重连提醒会不完整。"
            } else {
                "通知策略：前台服务提示链路已就绪。"
            }
            "当前模式：前台服务\n启动状态：可直接启动同步\n系统限制：${clipboardRestrictionSummary()}\n$batteryLine\n$notificationLine\n说明：这是最省心的模式；如果后台复制经常丢失，再切到无障碍或 Shizuku。"
        }
    }

    private fun buildAutoResumeSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
    ): String {
        if (!config.autoConnectEnabled) {
            return "自动续连：已关闭\n结果：每次都需要你手动点启动同步。"
        }
        if (config.serverBase.isBlank()) {
            return "自动续连：等待配置\n原因：还没有填写服务端地址，请先填 Windows 或服务器的局域网地址。"
        }
        if (isLoopbackServerBase(config.serverBase)) {
            return "自动续连：暂不可用\n原因：当前服务地址还是 127.0.0.1/localhost，请改成 Windows 局域网 IP。"
        }
        if (config.lastDesiredRunningState != SettingsStore.RUNNING_STATE_RUNNING) {
            return "自动续连：等待下次生效\n原因：上次是手动停止状态，后续即使重新打开 App，也不会自动恢复同步。"
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
        return "自动续连：已就绪\n${warnings.joinToString("；")}。"
    }

    private fun buildClipboardDebugSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
        validation: RuntimeModeValidation,
    ): String {
        val routeLine = when (val route = latestClipboardRoute.trim()) {
            "", "idle" -> "最近回传来源：尚未产生新的剪贴板事件"
            "remote" -> "最近回传来源：远端设备下发文本"
            "remote-apply" -> "最近回传来源：远端文本已写回本机剪贴板"
            "connected" -> "最近回传来源：刚建立同步连接"
            "trusted" -> "最近回传来源：设备刚切到已连接状态"
            "pending" -> "最近回传来源：设备仍在等待网页批准"
            "forbidden" -> "最近回传来源：房间认证失败"
            "startup-blocked" -> "最近回传来源：启动前校验拦截"
            else -> "最近回传来源：$route"
        }

        val modeLine = when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
                if (status.accessibilityEnabled) {
                    "当前监听策略：无障碍增强已开启，除了系统剪贴板回调，还会尝试用界面事件做补检查。"
                } else {
                    "当前监听策略：你选了无障碍增强，但系统无障碍还没打开，所以后台补传还不会生效。"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU ->
                "当前监听策略：Shizuku 入口已经展示，但独立增强链路还没接完，本阶段仍建议先用前台服务或无障碍模式。"

            else -> when {
                status.accessibilityEnabled ->
                    "当前监听策略：主通道仍是前台服务；如果前台回调没拿到新文本，后续可改成无障碍增强获得更稳的后台补传。"
                else ->
                    "当前监听策略：当前只依赖系统剪贴板回调和轮询，Android 10 以上后台限制会更明显。"
            }
        }

        val nextStepLine = when {
            !validation.ready -> "下一步建议：先按上面的模式引导补齐授权，再重新启动同步。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q ->
                "下一步建议：如果后台复制还是经常没有回传，优先切到无障碍增强模式。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY && !status.accessibilityEnabled ->
                "下一步建议：打开无障碍后再试一次后台复制。"
            latestClipboardRoute.startsWith("skip-") ->
                "下一步建议：当前有一次被跳过的本地检查，先看最近结果里的具体原因。"
            else ->
                "下一步建议：保持当前模式，做一次前台复制和一次后台复制，对比最近结果即可判断是否被系统限制。"
        }

        val detail = latestClipboardDetail.trim().ifBlank {
            "最近结果：还没有收到新的本地或远端剪贴板事件。"
        }
        return "$routeLine\n最近结果：$detail\n$modeLine\n$nextStepLine"
    }

    private fun buildPermissionSummary(
        status: PermissionStatus,
        checklist: StatusChecklist,
    ): String {
        val baseStatus = getString(
            R.string.permission_summary_format,
            stateLabel(status.notificationsEnabled),
            stateLabel(status.overlayEnabled),
            stateLabel(status.accessibilityEnabled),
            stateLabel(status.batteryOptimizationIgnored),
            stateLabel(status.shizukuInstalled),
        )
        val blockers = if (checklist.blockers.isEmpty()) {
            getString(R.string.permission_blockers_none)
        } else {
            checklist.blockers.joinToString("；")
        }
        val suggestions = if (checklist.suggestions.isEmpty()) {
            getString(R.string.permission_suggestions_none)
        } else {
            checklist.suggestions.joinToString("；")
        }
        return "系统版本：${androidVersionSummary()}\n$baseStatus\n\n当前阻塞项：$blockers\n建议优化项：$suggestions"
    }

    private fun buildPermissionGuide(checklist: StatusChecklist): String {
        if (checklist.blockers.isEmpty() && checklist.suggestions.isEmpty()) {
            return "当前常用权限和系统配置都已到位，可以直接使用同步、自动续连和悬浮确认。"
        }
        val lines = mutableListOf<String>()
        if (checklist.blockers.isNotEmpty()) {
            lines += "优先处理阻塞项："
            checklist.blockers.forEachIndexed { index, item ->
                lines += "${index + 1}. $item"
            }
        }
        if (checklist.suggestions.isNotEmpty()) {
            lines += "建议随后补齐："
            checklist.suggestions.forEachIndexed { index, item ->
                lines += "${index + 1}. $item"
            }
        }
        buildVendorBackgroundHint()?.let { hint ->
            lines += "厂商后台建议："
            lines += hint
        }
        return lines.joinToString("\n")
    }

    private fun buildPermissionChecklist(
        config: SettingsStore.Config,
        status: PermissionStatus,
    ): StatusChecklist {
        val blockers = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
                if (!status.accessibilityEnabled) {
                    blockers += "无障碍增强模式还没开启无障碍服务，当前模式下无法启动同步。"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                blockers += "Shizuku 模式的独立增强链路仍在接入中，当前版本请先改用前台服务或无障碍模式。"
            }
        }

        if (!status.notificationsEnabled) {
            suggestions += "建议开启通知权限，否则前台服务状态和接收确认提示会不完整。"
        }
        if (!status.batteryOptimizationIgnored) {
            suggestions += "建议忽略电池优化，尤其是澎湃 / MIUI 一类系统，否则后台容易被杀。"
        }
        buildVendorBackgroundChecklistTip()?.let { suggestions += it }
        if (config.floatingEnabled && !status.overlayEnabled) {
            suggestions += "已启用悬浮确认，但系统还没允许悬浮窗显示，图片/文件会回退到通知确认。"
        }
        if (config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && !status.accessibilityEnabled) {
            suggestions += "如果后续遇到后台复制不稳定，可以再开启无障碍增强模式。"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND) {
            suggestions += "Android 10 及以上系统会明显收紧后台剪贴板读取；如果你主要依赖后台复制回传，建议优先改成无障碍增强模式。"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND) {
            suggestions += "Android 14 及以上系统对后台读取剪贴板更严格，前台服务模式更适合前台使用；需要更稳的后台补传时，建议开启无障碍增强。"
        }
        if (!status.shizukuInstalled) {
            suggestions += "Shizuku 更适合系统限制明显的设备，需要时再安装并授权即可。"
        }

        return StatusChecklist(blockers = blockers, suggestions = suggestions)
    }

    private fun clipboardRestrictionSummary(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            "Android 14 及以上会更严格限制后台读取系统剪贴板，前台模式更适合你正在看着 App 的场景。"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            "Android 13 及以上会同时收紧通知和后台行为，前台服务状态、重连提醒和权限提示要一起补齐。"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            "Android 10 及以上开始明显限制后台读取剪贴板，单靠前台服务模式时，后台复制回传可能不稳定。"
        else ->
            "当前系统对后台剪贴板限制相对较少，但仍建议保留前台服务和电池优化设置。"
    }

    private fun androidVersionSummary(): String = buildString {
        append("Android ")
        append(Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT)
        append(" (API ")
        append(Build.VERSION.SDK_INT)
        append(")")
    }

    private fun buildVendorBackgroundHint(): String? {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                "澎湃 / MIUI 机型建议再检查一次自启动、后台弹出界面和无限制省电，否则无障碍与通知都开了也可能被系统回收。"
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "华为 / 荣耀机型建议把云剪同步加入启动管理和受保护应用，避免锁屏后后台同步被停掉。"
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                "OPPO / OnePlus / realme 机型建议补开自启动、后台活动和电池无限制，否则后台复制回传容易断。"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                "vivo / iQOO 机型建议补开后台高耗电允许、自启动和后台弹出界面。"
            manufacturer.contains("samsung") ->
                "三星机型建议把云剪同步移出睡眠应用或深度睡眠列表，避免长时间后台后被暂停。"
            else -> null
        }
    }

    private fun buildVendorBackgroundChecklistTip(): String? {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                "如果你用的是澎湃 / MIUI，建议再打开自启动、后台弹出界面和无限制省电。"
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "如果你用的是华为 / 荣耀，建议把云剪同步加入启动管理和受保护后台。"
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                "如果你用的是 OPPO / OnePlus / realme，建议补开自启动和后台活动权限。"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                "如果你用的是 vivo / iQOO，建议补开自启动、后台高耗电允许和后台弹出界面。"
            manufacturer.contains("samsung") ->
                "如果你用的是三星，建议检查电池中的睡眠应用列表，避免本应用被限制。"
            else -> null
        }
    }

    private fun buildReceiveCacheSummary(): String {
        val summary = PayloadCacheStore.summary(this)
        return getString(
            R.string.receive_cache_summary_format,
            summary.totalCount,
            summary.pendingCount,
            summary.processedCount,
            summary.snoozedCount,
            summary.downloadedCount,
            formatBytes(summary.totalSizeBytes),
        )
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> getString(R.string.receive_cache_size_gb, value / (1024f * 1024f * 1024f))
        value >= 1024L * 1024L -> getString(R.string.receive_cache_size_mb, value / (1024f * 1024f))
        value >= 1024L -> getString(R.string.receive_cache_size_kb, value / 1024f)
        else -> getString(R.string.receive_cache_size_bytes, value)
    }

    companion object {
        private const val TAB_CONNECTION = 0
        private const val TAB_RUNTIME = 1
        private const val TAB_PERMISSIONS = 2
        private const val TAB_RECEIVE = 3
    }
}

