package com.transparentlc.cloudclipboardsync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService
import rikka.shizuku.Shizuku

private data class StatusChecklist(
    val blockers: List<String>,
    val suggestions: List<String>,
)

class MainActivity : AppCompatActivity() {
    private lateinit var rootLayout: View
    private lateinit var homeHeaderShell: FrameLayout
    private lateinit var contentScrollView: ScrollView
    private lateinit var contentContainer: View
    private lateinit var settingsBottomNav: BottomNavigationView
    private lateinit var homeHeaderCard: View
    private lateinit var connectionSection: View
    private lateinit var connectionBottomSpacer: View
    private lateinit var runtimeSection: View
    private lateinit var permissionSection: View
    private lateinit var receiveSection: View
    private lateinit var runtimeSectionContent: View
    private lateinit var permissionSectionContent: View
    private lateinit var receiveSectionContent: View
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
    private lateinit var connectionSummaryText: TextView
    private lateinit var clipboardModeGroup: RadioGroup
    private lateinit var clipboardModeForeground: RadioButton
    private lateinit var clipboardModeFloating: RadioButton
    private lateinit var clipboardModeImeBackground: RadioButton
    private lateinit var clipboardModeShizuku: RadioButton
    private lateinit var autoConnectSwitch: CheckBox
    private lateinit var startOnBootSwitch: CheckBox
    private lateinit var closeAfterStartSwitch: CheckBox
    private lateinit var removeTaskSwitch: CheckBox
    private lateinit var floatingConfirmSwitch: CheckBox
    private lateinit var floatingCompactSwitch: CheckBox
    private lateinit var floatingAutoSendConfirmSwitch: CheckBox
    private lateinit var floatingAutoReceiveConfirmSwitch: CheckBox
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
    private lateinit var runtimeExplicitPreviewText: TextView
    private lateinit var runtimeExplicitSendSummaryText: TextView
    private lateinit var autoResumeSummaryText: TextView
    private lateinit var runtimeClipboardReadinessText: TextView
    private lateinit var runtimeClipboardTroubleshootButton: Button
    private lateinit var runtimeClipboardDebugText: TextView
    private lateinit var runtimeModeBadgeText: TextView
    private lateinit var permissionOverviewBadgeText: TextView
    private lateinit var runtimeModeActionButton: Button
    private lateinit var runtimeExplicitSendButton: Button
    private lateinit var runtimeExplicitGuideButton: Button
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

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != ShizukuPermissionHelper.REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) R.string.runtime_mode_action_shizuku_granted_toast else R.string.runtime_mode_action_shizuku_denied_toast,
            Toast.LENGTH_LONG,
        ).show()
        refreshRuntimeHints()
        refreshPermissionSummary()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = normalizeStatusText(
                intent?.getStringExtra(SyncService.EXTRA_STATUS) ?: getString(R.string.status_idle),
            )
            lastSyncText.text = intent?.getStringExtra(SyncService.EXTRA_LAST_RESULT) ?: getString(R.string.last_result_idle)
            latestClipboardRoute = intent?.getStringExtra(SyncService.EXTRA_CLIPBOARD_ROUTE) ?: latestClipboardRoute
            latestClipboardDetail = intent?.getStringExtra(SyncService.EXTRA_CLIPBOARD_DETAIL) ?: latestClipboardDetail
            refreshRuntimeHints()
            updateHomeHeaderSummary()
        }
    }

    private fun normalizeStatusText(status: String): String = when (status.trim().lowercase()) {
        "trusted", "已信任" -> getString(R.string.status_trusted)
        "connected" -> getString(R.string.status_connected)
        "pending" -> getString(R.string.status_pending)
        "forbidden" -> getString(R.string.status_forbidden)
        "disconnected" -> getString(R.string.status_disconnected)
        "connecting" -> getString(R.string.status_connecting)
        "idle" -> getString(R.string.status_idle)
        else -> status
    }

    private val payloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshRuntimeHints()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        homeHeaderShell = findViewById(R.id.homeHeaderShell)
        contentScrollView = findViewById(R.id.contentScrollView)
        contentContainer = findViewById(R.id.contentContainer)
        settingsBottomNav = findViewById(R.id.settingsBottomNav)
        homeHeaderCard = findViewById(R.id.homeHeaderCard)
        connectionSection = findViewById(R.id.connectionSection)
        connectionBottomSpacer = findViewById(R.id.connectionBottomSpacer)
        runtimeSection = findViewById(R.id.runtimeSection)
        permissionSection = findViewById(R.id.permissionSection)
        receiveSection = findViewById(R.id.receiveSection)
        runtimeSectionContent = findViewById(R.id.runtimeSectionContent)
        permissionSectionContent = findViewById(R.id.permissionSectionContent)
        receiveSectionContent = findViewById(R.id.receiveSectionContent)
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
        connectionSummaryText = findViewById(R.id.connectionSummaryText)
        clipboardModeGroup = findViewById(R.id.clipboardModeGroup)
        clipboardModeForeground = findViewById(R.id.clipboardModeForeground)
        clipboardModeFloating = findViewById(R.id.clipboardModeFloating)
        clipboardModeImeBackground = findViewById(R.id.clipboardModeImeBackground)
        clipboardModeShizuku = findViewById(R.id.clipboardModeShizuku)
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch)
        startOnBootSwitch = findViewById(R.id.startOnBootSwitch)
        closeAfterStartSwitch = findViewById(R.id.closeAfterStartSwitch)
        removeTaskSwitch = findViewById(R.id.removeTaskSwitch)
        floatingConfirmSwitch = findViewById(R.id.floatingConfirmSwitch)
        floatingCompactSwitch = findViewById(R.id.floatingCompactSwitch)
        floatingAutoSendConfirmSwitch = findViewById(R.id.floatingAutoSendConfirmSwitch)
        floatingAutoReceiveConfirmSwitch = findViewById(R.id.floatingAutoReceiveConfirmSwitch)
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
        runtimeExplicitPreviewText = findViewById(R.id.runtimeExplicitPreviewText)
        runtimeExplicitSendSummaryText = findViewById(R.id.runtimeExplicitSendSummaryText)
        autoResumeSummaryText = findViewById(R.id.autoResumeSummaryText)
        runtimeClipboardReadinessText = findViewById(R.id.runtimeClipboardReadinessText)
        runtimeClipboardTroubleshootButton = findViewById(R.id.runtimeClipboardTroubleshootButton)
        runtimeClipboardDebugText = findViewById(R.id.runtimeClipboardDebugText)
        runtimeModeBadgeText = findViewById(R.id.runtimeModeBadgeText)
        permissionOverviewBadgeText = findViewById(R.id.permissionOverviewBadgeText)
        runtimeModeActionButton = findViewById(R.id.runtimeModeActionButton)
        runtimeExplicitSendButton = findViewById(R.id.runtimeExplicitSendButton)
        runtimeExplicitGuideButton = findViewById(R.id.runtimeExplicitGuideButton)
        statusText = findViewById(R.id.statusText)
        lastSyncText = findViewById(R.id.lastSyncText)

        applyEdgeToEdgeInsets()
        bindBottomNav()
        bindHomeHeader()
        bindConfig()
        bindReceiveSection()
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
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
        findViewById<Button>(R.id.openSyncNotificationChannelButton).setOnClickListener {
            openNotificationChannelSettings(SyncService.CHANNEL_ID)
        }
        findViewById<Button>(R.id.openReceiveNotificationChannelButton).setOnClickListener {
            openNotificationChannelSettings(SyncService.RECEIVE_CHANNEL_ID)
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
        runtimeExplicitSendButton.setOnClickListener {
            handleExplicitSendManualAction()
        }
        runtimeExplicitGuideButton.setOnClickListener {
            showExplicitSendGuide()
        }
        runtimeClipboardTroubleshootButton.setOnClickListener {
            handleClipboardTroubleshootAction()
        }
        clipboardModeGroup.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) {
                saveConfig()
            }
            refreshRuntimeHints()
        }
        autoConnectSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) {
                saveConfig()
            }
            refreshRuntimeHints()
        }
        startOnBootSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) {
                saveConfig()
            }
            refreshRuntimeHints()
        }
        floatingConfirmSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) saveReceiveSettings()
        }
        floatingCompactSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) saveReceiveSettings()
        }
        floatingAutoSendConfirmSwitch.setOnCheckedChangeListener { _, _ ->
            if (!suppressAutoSave) saveReceiveSettings()
        }
        floatingAutoReceiveConfirmSwitch.setOnCheckedChangeListener { _, _ ->
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

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
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
        val migration = SettingsStore.loadWithMigration(this)
        val config = migration.config
        serverBaseInput.setText(config.serverBase)
        roomInput.setText(config.room)
        roomPasswordInput.setText(config.roomPassword)
        deviceNameInput.setText(config.deviceName)
        when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_FLOATING -> clipboardModeFloating.isChecked = true
            SettingsStore.CLIPBOARD_MODE_IME_BACKGROUND -> clipboardModeImeBackground.isChecked = true
            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> clipboardModeShizuku.isChecked = true
            else -> clipboardModeForeground.isChecked = true
        }
        autoConnectSwitch.isChecked = config.autoConnectEnabled
        startOnBootSwitch.isChecked = config.startOnBootEnabled
        closeAfterStartSwitch.isChecked = config.closeActivityAfterStart
        removeTaskSwitch.isChecked = config.removeTaskFromRecents
        floatingConfirmSwitch.isChecked = config.floatingEnabled
        floatingCompactSwitch.isChecked = config.floatingCompactEnabled
        floatingAutoSendConfirmSwitch.isChecked = config.floatingAutoSendConfirmEnabled
        floatingAutoReceiveConfirmSwitch.isChecked = config.floatingAutoReceiveConfirmEnabled
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
        if (migration.modeMigrated) {
            val message = getString(
                R.string.clipboard_mode_migrated_to_foreground,
                legacyClipboardModeLabel(migration.previousMode),
            )
            lastSyncText.text = message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun legacyClipboardModeLabel(rawMode: String?): String = when (rawMode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> getString(R.string.clipboard_mode_legacy_accessibility)
        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> getString(R.string.clipboard_mode_legacy_shizuku)
        SettingsStore.CLIPBOARD_MODE_IME -> getString(R.string.clipboard_mode_legacy_ime)
        else -> getString(R.string.clipboard_mode_legacy_unknown)
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
        homeHeaderShell.visibility = if (selectedTabIndex == TAB_CONNECTION) View.VISIBLE else View.GONE
        connectionSection.visibility = if (selectedTabIndex == TAB_CONNECTION) View.VISIBLE else View.GONE
        runtimeSection.visibility = if (selectedTabIndex == TAB_RUNTIME) View.VISIBLE else View.GONE
        permissionSection.visibility = if (selectedTabIndex == TAB_PERMISSIONS) View.VISIBLE else View.GONE
        receiveSection.visibility = if (selectedTabIndex == TAB_RECEIVE) View.VISIBLE else View.GONE
        contentScrollView.post { contentScrollView.scrollTo(0, 0) }
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
            floatingAutoSendConfirmEnabled = floatingAutoSendConfirmSwitch.isChecked,
            floatingAutoReceiveConfirmEnabled = floatingAutoReceiveConfirmSwitch.isChecked,
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
        R.id.clipboardModeFloating -> SettingsStore.CLIPBOARD_MODE_FLOATING
        R.id.clipboardModeImeBackground -> SettingsStore.CLIPBOARD_MODE_IME_BACKGROUND
        R.id.clipboardModeShizuku -> SettingsStore.CLIPBOARD_MODE_SHIZUKU
        else -> SettingsStore.CLIPBOARD_MODE_FOREGROUND
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            homeHeaderShell.setPadding(
                homeHeaderShell.paddingLeft,
                systemBars.top,
                homeHeaderShell.paddingRight,
                homeHeaderShell.paddingBottom,
            )
            homeHeaderCard.setPadding(
                homeHeaderCard.paddingLeft,
                dpToPx(22),
                homeHeaderCard.paddingRight,
                homeHeaderCard.paddingBottom,
            )
            val layoutParams = homeHeaderCard.layoutParams as FrameLayout.LayoutParams
            layoutParams.topMargin = dpToPx(4)
            homeHeaderCard.layoutParams = layoutParams

            val immersiveTopPadding = systemBars.top + dpToPx(20)
            runtimeSectionContent.setPadding(
                runtimeSectionContent.paddingLeft,
                immersiveTopPadding,
                runtimeSectionContent.paddingRight,
                runtimeSectionContent.paddingBottom,
            )
            permissionSectionContent.setPadding(
                permissionSectionContent.paddingLeft,
                immersiveTopPadding,
                permissionSectionContent.paddingRight,
                permissionSectionContent.paddingBottom,
            )
            receiveSectionContent.setPadding(
                receiveSectionContent.paddingLeft,
                immersiveTopPadding,
                receiveSectionContent.paddingRight,
                receiveSectionContent.paddingBottom,
            )

            contentScrollView.setPadding(
                contentScrollView.paddingLeft,
                contentScrollView.paddingTop,
                contentScrollView.paddingRight,
                systemBars.bottom + dpToPx(12),
            )
            settingsBottomNav.setPadding(
                settingsBottomNav.paddingLeft,
                settingsBottomNav.paddingTop,
                settingsBottomNav.paddingRight,
                systemBars.bottom + dpToPx(10),
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun saveReceiveSettings(refreshAfter: Boolean = true) {
        if (suppressAutoSave) {
            return
        }
        val previous = SettingsStore.load(this)
        val updated = previous.copy(
            floatingEnabled = floatingConfirmSwitch.isChecked,
            floatingCompactEnabled = floatingCompactSwitch.isChecked,
            floatingAutoSendConfirmEnabled = floatingAutoSendConfirmSwitch.isChecked,
            floatingAutoReceiveConfirmEnabled = floatingAutoReceiveConfirmSwitch.isChecked,
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
        val preview = ManualClipboardSender.buildClipboardPreview(this)
        runtimeExplicitPreviewText.text = preview.text
        runtimeExplicitSendSummaryText.text = buildExplicitSendSummary(config, status, preview.empty)
        runtimeExplicitSendButton.isEnabled = !preview.empty
        autoResumeSummaryText.text = buildAutoResumeSummary(config, status)
        runtimeClipboardReadinessText.text = buildClipboardReadinessSummary(config, status, validation)
        runtimeClipboardTroubleshootButton.text = clipboardTroubleshootActionLabel(config, status, validation)
        runtimeClipboardDebugText.text = buildClipboardDebugSummary(config, status, validation)
        connectionSummaryText.text = buildConnectionSummary(config)
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
            else -> buildString {
                append(getString(R.string.receive_overlay_ready_summary))
                if (config.floatingAutoSendConfirmEnabled) {
                    append("\n")
                    append(getString(R.string.receive_overlay_auto_send_ready_summary))
                }
                if (config.floatingAutoReceiveConfirmEnabled) {
                    append("\n")
                    append(getString(R.string.receive_overlay_auto_receive_ready_summary))
                }
                append("\n")
                append(
                    if (config.floatingCompactEnabled) {
                        "当前已启用紧凑卡片，会优先显示标题和操作按钮。"
                    } else {
                        "当前使用详细卡片，会显示来源、大小和操作提示。"
                    },
                )
            }
        }
        receiveCacheSummaryText.text = buildReceiveCacheSummary()
        refreshFloatingDraftSummary()
        updateHomeHeaderSummary()
    }

    private fun buildConnectionSummary(config: SettingsStore.Config): String {
        val roomLabel = config.room.ifBlank { "默认房间" }
        val deviceLabel = config.deviceName.ifBlank { "当前设备" }
        return getString(R.string.connection_summary_format, deviceLabel, roomLabel)
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
        val autoConfirmSummary = buildList {
            if (floatingAutoSendConfirmSwitch.isChecked) add("发送自动确认已开")
            if (floatingAutoReceiveConfirmSwitch.isChecked) add("接收自动确认已开")
        }.joinToString(" · ").ifBlank { "自动确认均关闭" }
        floatingLayoutSummaryText.text = getString(
            R.string.floating_layout_summary_format,
            stored.floatingPosX,
            stored.floatingPosY,
            width,
            height,
            showSeconds,
            snoozeMinutes,
        ) + "\n" + compactLabel + "\n" + autoConfirmSummary
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
            SettingsStore.CLIPBOARD_MODE_FLOATING -> {
                when {
                    !status.overlayEnabled -> openOverlaySettings()
                    !status.notificationsEnabled -> openNotificationSettings()
                    else -> FloatingClipboardOverlayService.show(this)
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

    private fun handleExplicitSendManualAction() {
        val route = when (SettingsStore.load(this).clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_FLOATING -> "floating-panel"
            else -> "explicit-send-panel"
        }
        ManualClipboardSender.sendCurrentClipboardText(
            context = this,
            route = route,
        ) { message ->
            lastSyncText.text = message
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExplicitSendGuide() {
        Toast.makeText(this, R.string.runtime_explicit_send_guide_toast, Toast.LENGTH_LONG).show()
    }

    private fun handleClipboardTroubleshootAction() {
        val config = SettingsStore.load(this)
        val status = PermissionStatusHelper.read(this)
        val validation = RuntimeModeValidator.validate(this, config)
        when {
            !validation.ready -> openRuntimeModeAction(validation.action)
            !status.notificationsEnabled -> openNotificationSettings()
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                showExplicitSendGuide()
            }
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING && !status.overlayEnabled -> openOverlaySettings()
            !status.batteryOptimizationIgnored -> openBatteryOptimizationSettings()
            shouldSuggestVendorBackgroundSettings() -> openVendorBackgroundSettings()
            else -> Toast.makeText(this, R.string.runtime_clipboard_diagnosis_ready_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun clipboardTroubleshootActionLabel(
        config: SettingsStore.Config,
        status: PermissionStatus,
        validation: RuntimeModeValidation,
    ): String = when {
        !validation.ready -> runtimeModeActionLabel(config, status)
        !status.notificationsEnabled -> getString(R.string.open_notification_settings_button)
        config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            getString(R.string.runtime_clipboard_switch_accessibility_button)
        config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING && !status.overlayEnabled ->
            getString(R.string.runtime_mode_action_floating)
        !status.batteryOptimizationIgnored -> getString(R.string.runtime_mode_action_battery)
        shouldSuggestVendorBackgroundSettings() -> getString(R.string.open_vendor_background_settings_button)
        else -> getString(R.string.runtime_clipboard_troubleshoot_button)
    }

    private fun openRuntimeModeAction(action: RuntimeModeAction) {
        when (action) {
            RuntimeModeAction.OPEN_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            RuntimeModeAction.OPEN_FLOATING -> {
                openOverlaySettings()
            }

            RuntimeModeAction.OPEN_SHIZUKU -> {
                val status = PermissionStatusHelper.read(this)
                when {
                    status.shizukuRunning && !status.shizukuPermissionGranted -> requestShizukuPermission()
                    else -> openShizuku(status.shizukuInstalled)
                }
            }

            RuntimeModeAction.OPEN_IME_SETTINGS -> {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
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

    private fun openNotificationChannelSettings(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openNotificationSettings()
            return
        }
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        runCatching { startActivity(intent) }
            .onFailure { openNotificationSettings() }
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

    private fun requestShizukuPermission() {
        val requested = ShizukuPermissionHelper.requestPermission()
        if (!requested) {
            Toast.makeText(this, R.string.runtime_mode_action_shizuku_not_running_toast, Toast.LENGTH_LONG).show()
            openShizuku(PermissionStatusHelper.read(this).shizukuInstalled)
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
            } else if (!status.shizukuRunning) {
                R.string.runtime_mode_action_shizuku
            } else if (!status.shizukuPermissionGranted) {
                R.string.runtime_mode_action_shizuku_authorize
            } else if (!status.notificationsEnabled) {
                R.string.open_notification_settings_button
            } else {
                R.string.runtime_mode_action_shizuku_ready
            },
        )

        SettingsStore.CLIPBOARD_MODE_FLOATING -> when {
            !status.overlayEnabled -> getString(R.string.runtime_mode_action_floating)
            !status.notificationsEnabled -> getString(R.string.open_notification_settings_button)
            else -> getString(R.string.runtime_mode_action_floating_ready)
        }

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
                    readyItems += "无障碍辅助链路${status.accessibilityDetail}"
                    readyItems += "仅兼容旧配置保留"
                } else {
                    pendingItems += "旧配置仍指向无障碍辅助链路，需要先开启无障碍服务"
                    pendingItems += "建议改用前台服务、原键盘发送或悬浮窗模式"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                when {
                    !status.shizukuInstalled -> {
                    pendingItems += "需要先安装或拉起 Shizuku"
                    }
                    !status.shizukuRunning -> {
                        readyItems += "已安装 Shizuku"
                        pendingItems += "需要先启动 Shizuku 服务"
                    }
                    !status.shizukuPermissionGranted -> {
                        readyItems += "Shizuku 服务已运行"
                        pendingItems += "需要授权云剪同步访问 Shizuku"
                    }
                    else -> {
                        readyItems += "Shizuku 已授权${status.shizukuUid?.let { "（UID $it）" }.orEmpty()}"
                        readyItems += "已纳入系统授权与剪贴板 AppOps 诊断"
                        pendingItems += "建议改用前台服务、原键盘发送或悬浮窗模式"
                    }
                }
            }

            SettingsStore.CLIPBOARD_MODE_FLOATING -> {
                if (status.overlayEnabled) {
                    readyItems += "悬浮窗权限已开启"
                    readyItems += "可作为复制后快速发送助手入口"
                } else {
                    pendingItems += "需要先允许悬浮窗显示"
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

    private fun buildExplicitSendSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
        clipboardEmpty: Boolean,
    ): String {
        val modeLine = when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_FLOATING ->
                "当前正式模式：悬浮窗模式。它负责复制后的快速发送助手；这里这张卡片则负责随时手动发一次当前剪贴板文本。"
            else ->
                "当前正式模式：前台服务模式。后台复制如果受系统限制，可以直接在这里手动发送当前剪贴板文本。"
        }
        val clipboardLine = if (clipboardEmpty) {
            "当前剪贴板：还没有可发送的文本；你可以先去别的应用复制一段文字，再回到这里发送。"
        } else {
            "当前剪贴板：已检测到可发送文本；点下面的按钮会直接复用正式文本发布主链路，不会要求切换默认输入法。"
        }
        val routeLine = "可用入口：1. 本页“发送当前剪贴板文本”；2. 系统分享里的“分享到云剪同步”；3. 选中文本后的系统处理菜单。"
        val hintLine = if (!status.notificationsEnabled) {
            "补充提示：建议把通知权限也打开，方便看前台服务状态和发送结果。"
        } else {
            "补充提示：这条原键盘发送能力会一直保留，作为不替换默认输入法时的通用兜底。"
        }
        return "$modeLine\n$clipboardLine\n$routeLine\n$hintLine"
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

    private fun shizukuStateLabel(status: PermissionStatus): String = when {
        !status.shizukuInstalled -> "未安装"
        !status.shizukuRunning -> "已安装，未运行"
        !status.shizukuPermissionGranted -> "服务运行，未授权"
        else -> "已授权${status.shizukuUid?.let { "（UID $it）" }.orEmpty()}"
    }

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
                "当前辅助状态：无障碍辅助链路（兼容旧配置）\n启动状态：可直接启动同步\n授权状态：${status.accessibilityDetail}\n系统限制：${clipboardRestrictionSummary()}\n说明：除了系统剪贴板回调，还会在界面交互时主动触发补检查；但这条路线现在只作为兼容旧配置与辅助授权保留，不再作为正式推荐主模式。"
            } else {
                "当前辅助状态：无障碍辅助链路（兼容旧配置）\n启动状态：需要处理\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：这条路线只作为兼容旧配置与辅助授权保留，不再作为正式推荐主模式；建议改用前台服务、原键盘发送或悬浮窗模式。"
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> "当前诊断状态：Shizuku\n启动状态：需要处理\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：先安装 Shizuku，再用 root 或 adb 启动服务。"
                !status.shizukuRunning -> "当前诊断状态：Shizuku\n启动状态：需要处理\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：当前已安装 Shizuku，但服务还没运行；root 启动后回到这里刷新状态。"
                !status.shizukuPermissionGranted -> "当前诊断状态：Shizuku\n启动状态：需要处理\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：Shizuku 服务已运行，点快捷处理按钮授权云剪同步。"
                else -> "当前诊断状态：Shizuku 辅助诊断\n启动状态：可直接启动同步\n系统限制：${clipboardRestrictionSummary()}\nAppOps：读取 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardReadAppOp)} / 写入 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardWriteAppOp)}\n说明：Shizuku 已授权${status.shizukuUid?.let { "（UID $it）" }.orEmpty()}；${PermissionStatusHelper.clipboardReadRestrictionLabel(status.clipboardReadAppOp)}当前只作为系统授权和剪贴板 AppOps 诊断辅助，不再额外轮询系统剪贴板，也不承诺绕过后台剪贴板限制。正式推荐模式请改用前台服务、原键盘发送或悬浮窗。"
            }
        }

        SettingsStore.CLIPBOARD_MODE_FLOATING -> {
            if (status.overlayEnabled) {
                "当前模式：悬浮窗模式\n启动状态：可直接启动同步\n系统限制：${clipboardRestrictionSummary()}\n悬浮窗状态：已允许显示\n说明：当前先把悬浮窗模式作为复制后快速发送助手的正式入口，后续会继续补更轻量的发送浮标。"
            } else {
                "当前模式：悬浮窗模式\n启动状态：需要处理\n原因：${validation.message}\n系统限制：${clipboardRestrictionSummary()}\n说明：先补开悬浮窗权限，后续可作为复制后快速发送助手使用。"
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
            "当前模式：前台服务\n启动状态：可直接启动同步\n系统限制：${clipboardRestrictionSummary()}\n$batteryLine\n$notificationLine\n说明：这是当前默认、最省心的正式模式；如果后台复制经常丢失，优先改用原键盘发送或悬浮窗模式做兜底。"
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
            return "自动续连：未启用\n原因：当前服务地址还是 127.0.0.1/localhost，请改成 Windows 局域网 IP。"
        }
        if (config.lastDesiredRunningState != SettingsStore.RUNNING_STATE_RUNNING) {
            return "自动续连：等待手动启动\n原因：上次是手动停止状态，重新打开 App 时不会自动恢复同步。"
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

    private fun buildClipboardReadinessSummary(
        config: SettingsStore.Config,
        status: PermissionStatus,
        validation: RuntimeModeValidation,
    ): String {
        val readiness = when {
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU && status.shizukuPermissionGranted -> "后台复制就绪度：已授权，当前为辅助诊断模式"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU -> "后台复制就绪度：等待 Shizuku 授权"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING && status.overlayEnabled -> "后台复制就绪度：助手入口已就绪"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING -> "后台复制就绪度：等待悬浮窗权限"
            !validation.ready -> "后台复制就绪度：当前被拦截"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY && status.accessibilityEnabled -> "后台复制就绪度：辅助链路已就绪"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "后台复制就绪度：受限"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "后台复制就绪度：一般"
            else -> "后台复制就绪度：可用"
        }

        val reason = when {
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU ->
                "原因：${status.shizukuDetail}；剪贴板 AppOps 为读取 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardReadAppOp)} / 写入 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardWriteAppOp)}。${PermissionStatusHelper.clipboardReadRestrictionLabel(status.clipboardReadAppOp)}当前 Shizuku 只作为系统授权与诊断辅助链路，不承诺绕过后台剪贴板限制。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING ->
                "原因：悬浮窗模式当前先提供复制后快速发送助手入口，依赖悬浮窗权限，不承诺绕过后台系统剪贴板限制。"
            !validation.ready -> "原因：${validation.message}"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY && status.accessibilityEnabled ->
                "原因：当前仍命中兼容旧配置的无障碍辅助链路${status.accessibilityDetail}，系统剪贴板回调之外还会做界面事件补检查。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                "原因：Android 14 及以上对后台读取系统剪贴板限制更严，前台服务模式更适合你正在看着 App 的场景。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                "原因：Android 10 及以上已经明显收紧后台剪贴板读取，单靠前台服务时后台复制回传可能不稳定。"
            else ->
                "原因：当前系统限制相对少，现有前台服务链路通常可以覆盖日常复制场景。"
        }

        val risk = when {
            !status.notificationsEnabled -> "风险提示：通知权限未开启，前台服务状态和失败提醒会不完整。"
            !status.batteryOptimizationIgnored -> "风险提示：还没忽略电池优化，系统可能会后台回收同步服务。"
            shouldSuggestVendorBackgroundSettings() -> "风险提示：当前机型仍建议再检查一次厂商后台保活设置。"
            else -> "风险提示：当前常见后台限制项已基本补齐。"
        }

        val nextStep = when {
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU && status.shizukuPermissionGranted ->
                "下一步：可以启动同步并做一次前台/后台复制对照；如果后台复制仍没回传，这是系统限制下的预期现象，正式使用请优先改用前台服务、原键盘发送或悬浮窗模式。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU ->
                "下一步：先启动 Shizuku 服务并完成授权；如果要继续日常同步，正式推荐仍是前台服务、原键盘发送或悬浮窗模式。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING && status.overlayEnabled ->
                "下一步：保持当前模式，后续继续结合悬浮助手入口做联调；当前先确认悬浮窗权限和通知链路正常。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING ->
                "下一步：先补开悬浮窗权限，再继续联调复制后快速发送助手入口。"
            !validation.ready -> "下一步：先点上面的快捷处理按钮补齐当前模式所需授权。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                "下一步：先做一次前台复制和一次后台复制；如果后台经常没回传，优先改用原键盘发送或悬浮窗模式做兜底。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY && !status.batteryOptimizationIgnored ->
                "下一步：补开忽略电池优化，再测一次这条辅助链路在锁屏或切后台后的复制回传。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY && shouldSuggestVendorBackgroundSettings() ->
                "下一步：去厂商后台保活页面补开自启动或无限制省电，再测一次这条辅助链路的后台复制表现。"
            else ->
                "下一步：保持当前模式，分别做一次前台复制和后台复制，对照下面的诊断结果看是否被系统限制。"
        }

        return "$readiness\n$reason\n$risk\n$nextStep"
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
                    "当前监听策略：无障碍辅助链路${status.accessibilityDetail}，当前仅兼容旧配置保留；除了系统剪贴板回调，还会尝试用界面事件做补检查。"
                } else {
                    "当前监听策略：你当前落在旧的无障碍辅助配置，但系统无障碍还没打开，所以这条辅助链路还不会生效。"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU ->
                "当前监听策略：${status.shizukuDetail}；剪贴板 AppOps 为读取 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardReadAppOp)} / 写入 ${PermissionStatusHelper.clipboardAppOpLabel(status.clipboardWriteAppOp)}。${PermissionStatusHelper.clipboardReadRestrictionLabel(status.clipboardReadAppOp)}当前仅保留系统剪贴板回调，并把 Shizuku 状态作为诊断辅助信息展示，不再额外轮询系统剪贴板。"

            SettingsStore.CLIPBOARD_MODE_FLOATING ->
                "当前监听策略：当前主要依赖悬浮窗权限和后续快速发送助手入口，暂时不把它描述成自动后台读取方案。"

            else -> when {
                status.accessibilityEnabled ->
                    "当前监听策略：主通道仍是前台服务；当前设备上无障碍辅助链路也已就绪，但正式推荐的兜底路线优先是原键盘发送或悬浮窗模式。"
                else ->
                    "当前监听策略：当前只依赖系统剪贴板回调和轮询，Android 10 以上后台限制会更明显。"
            }
        }

        val nextStepLine = when {
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU && status.shizukuPermissionGranted ->
                "下一步建议：Shizuku 授权已经通过，可以启动同步并观察最近结果；如果后台复制仍没回传，正式使用请优先改用前台服务、原键盘发送或悬浮窗模式。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_SHIZUKU ->
                "下一步建议：先启动 Shizuku 服务并完成授权；日常同步正式推荐仍是前台服务、原键盘发送或悬浮窗模式。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING && !status.overlayEnabled ->
                "下一步建议：先补开悬浮窗权限，再继续联调复制后快速发送助手。"
            !validation.ready -> "下一步建议：先按上面的模式引导补齐授权，再重新启动同步。"
            config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q ->
                "下一步建议：如果后台复制还是经常没有回传，优先改用原键盘发送或悬浮窗模式。"
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
            status.accessibilityDetail,
            status.imeDetail,
            stateLabel(status.batteryOptimizationIgnored),
            shizukuStateLabel(status),
            status.clipboardReadAppOp,
            status.clipboardWriteAppOp,
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
                    blockers += "历史配置仍停在无障碍辅助链路，但系统无障碍服务还没开启。"
                } else {
                    suggestions += "当前只是兼容旧配置保留的无障碍辅助链路；正式推荐模式请改用前台服务、原键盘发送或悬浮窗。"
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                when {
                    !status.shizukuInstalled -> blockers += "当前仍落在 Shizuku 辅助诊断链路，但设备还没有安装 Shizuku。"
                    !status.shizukuRunning -> blockers += "当前仍落在 Shizuku 辅助诊断链路，但 Shizuku 服务还没运行。"
                    !status.shizukuPermissionGranted -> blockers += "当前仍落在 Shizuku 辅助诊断链路，但云剪同步还没有获得 Shizuku 授权。"
                    else -> suggestions += "Shizuku 已授权；当前只作为系统授权与剪贴板 AppOps 诊断辅助保留，正式推荐模式请改用前台服务、原键盘发送或悬浮窗。"
                }
            }

            SettingsStore.CLIPBOARD_MODE_FLOATING -> {
                if (!status.overlayEnabled) {
                    blockers += "当前选择悬浮窗模式，但系统还没允许悬浮窗显示。"
                } else {
                    suggestions += "悬浮窗模式基础权限已到位，后续可继续联调复制后快速发送助手。"
                }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND) {
            suggestions += "Android 10 及以上系统会明显收紧后台剪贴板读取；如果你主要依赖后台复制回传，建议优先改用原键盘发送或悬浮窗模式。"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND) {
            suggestions += "Android 14 及以上系统对后台读取剪贴板更严格，前台服务模式更适合前台使用；需要更稳的兜底发送时，建议改用原键盘发送或悬浮窗模式。"
        }
        if (!status.shizukuInstalled) {
            suggestions += "如需查看系统授权与剪贴板 AppOps 诊断信息，可按需安装 Shizuku；日常同步不再依赖它。"
        } else if (!status.shizukuRunning) {
            suggestions += "已安装 Shizuku；如需查看诊断信息，请先用 root 或 adb 启动 Shizuku 服务。"
        } else if (!status.shizukuPermissionGranted) {
            suggestions += "Shizuku 服务已运行；如需查看诊断信息，请在运行页处理授权。"
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
