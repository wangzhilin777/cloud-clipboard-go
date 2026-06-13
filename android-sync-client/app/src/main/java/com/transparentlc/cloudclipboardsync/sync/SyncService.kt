package com.transparentlc.cloudclipboardsync.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.transparentlc.cloudclipboardsync.ClipboardAccessAccessibilityService
import com.transparentlc.cloudclipboardsync.FloatingClipboardOverlayService
import com.transparentlc.cloudclipboardsync.FloatingConfirmService
import com.transparentlc.cloudclipboardsync.ReceivedPayloadActivity
import com.transparentlc.cloudclipboardsync.MainActivity
import com.transparentlc.cloudclipboardsync.PermissionStatusHelper
import com.transparentlc.cloudclipboardsync.R
import com.transparentlc.cloudclipboardsync.RuntimeModeValidator
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.abs

class SyncService : Service() {
    private val reconnectDelaysMs = longArrayOf(2_000L, 2_000L, 2_000L)
    private val clipboardPollIntervalMs = 1500L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var config: SettingsStore.Config
    private var client: ClipboardSyncClient? = null
    private var applyingRemoteText = false
    private var trusted = false
    private var lastRemoteText = ""
    private var lastRemoteAt = 0L
    private var lastRemoteMessageId = ""
    private var suppressedRemoteEchoText = ""
    private var lastPublishedText = ""
    private var lastPublishedAt = 0L
    private var lastObservedLocalText = ""
    private var serviceStarted = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private val recentPublishedTexts = mutableMapOf<String, Long>()
    private val downloadingPayloads = mutableSetOf<String>()
    private var lastClipboardRoute = "idle"
    private var lastClipboardDetail = "等待开始"
    private var pendingDebugPublishText = ""
    private var pendingManualPublishText = ""

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        publishLocalClipboardIfNeeded("listener")
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            Thread(::refreshTrustState).start()
            handler.postDelayed(this, 8_000)
        }
    }

    private val clipboardPollRunnable = object : Runnable {
        override fun run() {
            if (config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FOREGROUND ||
                config.clipboardMode == SettingsStore.CLIPBOARD_MODE_IME_BACKGROUND) {
                publishLocalClipboardIfNeeded("poll")
            }
            handler.postDelayed(this, clipboardPollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        isRunning = true
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        PayloadCacheStore.pruneExpired(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshConfig()
        PayloadCacheStore.pruneExpired(this)
        val debugPublishIntent = intent?.action == ACTION_DEBUG_PUBLISH_TEXT
        val manualPublishIntent = intent?.action == ACTION_SEND_MANUAL_TEXT
        val serverBaseMessage = when {
            config.serverBase.isBlank() -> getString(R.string.server_base_missing_hint)
            SettingsStore.isLoopbackServerBase(config.serverBase) -> getString(R.string.server_base_loopback_hint)
            else -> null
        }
        if (serverBaseMessage != null) {
            return stopStartupWithMessage(serverBaseMessage)
        }
        val runtimeValidation = RuntimeModeValidator.validate(this, config)
        if (!debugPublishIntent && !manualPublishIntent && !runtimeValidation.ready) {
            return stopStartupWithMessage(runtimeValidation.message)
        }
        when (intent?.action) {
            ACTION_CONFIRM_PAYLOAD -> intent.getStringExtra(EXTRA_PAYLOAD_ID)?.let(::confirmPayloadDownload)
            ACTION_ACCESSIBILITY_PULSE -> handleAccessibilityPulse(intent)
            ACTION_DEBUG_PUBLISH_TEXT -> queueDebugPublish(intent)
            ACTION_SEND_MANUAL_TEXT -> queueManualPublish(intent)
        }
        if (!serviceStarted) {
            lastObservedLocalText = readCurrentClipboardText()
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
            handler.post(refreshRunnable)
            handler.post(clipboardPollRunnable)
            serviceStarted = true
            reconnectNow("service-start")
        } else if ((debugPublishIntent || manualPublishIntent) && trusted) {
            flushPendingDebugPublishText()
            flushPendingManualPublishText()
        } else {
            reconnectNow("manual-start")
        }
        return START_STICKY
    }

    private fun stopStartupWithMessage(message: String): Int {
        startForeground(NOTIFICATION_ID, buildNotification(message))
        updateClipboardDiagnostic("startup-blocked", message)
        broadcastStatus(getString(R.string.status_idle), message)
        showReconnectFailureAlert(message)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        client?.disconnect()
        client = null
        removeForegroundNotification()
        isRunning = false
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    private fun removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        client?.disconnect()
        client = ClipboardSyncClient(config, object : ClipboardSyncClient.Callbacks {
            override fun onConnected() {
                reconnectAttempt = 0
                updateClipboardDiagnostic("connected", "同步连接已建立，等待本地或远端剪贴板事件")
                broadcastStatus(getString(R.string.status_connected), "同步连接已建立")
                updateNotification(getString(R.string.status_connected))
                flushPendingDebugPublishText()
                flushPendingManualPublishText()
            }

            override fun onTrustedChanged(trusted: Boolean) {
                this@SyncService.trusted = trusted
                val status = if (trusted) getString(R.string.status_trusted) else getString(R.string.status_pending)
                updateClipboardDiagnostic(
                    if (trusted) "trusted" else "pending",
                    if (trusted) "设备已连接，可开始处理本地和远端剪贴板" else "设备等待网页批准，当前不会正式同步内容",
                )
                broadcastStatus(status, if (trusted) "设备已连接" else "设备等待网页批准")
                updateNotification(status)
                if (trusted) {
                    flushPendingDebugPublishText()
                    flushPendingManualPublishText()
                }
            }

            override fun onRemoteText(messageId: String, text: String) {
                updateClipboardDiagnostic("remote", "已收到远端文本，准备写入系统剪贴板")
                applyRemoteText(messageId, text, "已接收远端文本并写入剪贴板")
            }

            override fun onPayloadNotice(notice: PayloadNotice) {
                val existing = PayloadCacheStore.get(this@SyncService, notice.payloadId)
                val entry = PayloadCacheStore.upsertNotice(this@SyncService, notice)
                if (existing?.isDownloaded != true && !PayloadCacheStore.isSnoozed(entry)) {
                    if (shouldUseFloatingConfirm()) {
                        FloatingConfirmService.show(this@SyncService, entry.payloadId)
                    } else {
                        showPayloadNoticeNotification(entry)
                    }
                }
                broadcastStatus(currentStatus(), "收到${describeKind(entry.kind)}：${entry.title}")
            }

            override fun onLog(message: String) {
                broadcastStatus(currentStatus(), message)
            }

            override fun onForbidden() {
                reconnectAttempt = 0
                updateClipboardDiagnostic("forbidden", "房间认证失败，请检查房间密码或全局密码")
                broadcastStatus(getString(R.string.status_forbidden), "认证失败")
                updateNotification(getString(R.string.status_forbidden))
            }

            override fun onDisconnected() {
                scheduleReconnectOrStop()
            }
        })
        client?.connect()
    }

    private fun reconnectNow(reason: String) {
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = null
        reconnectAttempt = 0
        trusted = false
        updateClipboardDiagnostic(reason, "正在重新建立同步连接")
        broadcastStatus(getString(R.string.status_connecting), "正在重新建立同步连接")
        updateNotification(getString(R.string.status_connecting))
        connect()
    }

    private fun scheduleReconnectOrStop() {
        if (reconnectAttempt >= reconnectDelaysMs.size) {
            reconnectAttempt = 0
            val message = getString(R.string.reconnect_failure_limit_message)
            updateClipboardDiagnostic("reconnect-stopped", message)
            broadcastStatus(getString(R.string.status_disconnected), message)
            updateNotification(getString(R.string.status_disconnected))
            showReconnectFailureAlert(message)
            return
        }
        val attempt = reconnectAttempt + 1
        val delayMs = reconnectDelaysMs[reconnectAttempt]
        reconnectAttempt++
        val message = getString(R.string.reconnect_retry_message, attempt, reconnectDelaysMs.size)
        updateClipboardDiagnostic("reconnect-$attempt", "$message，等待 ${delayMs / 1000} 秒后再试")
        broadcastStatus(getString(R.string.status_disconnected), message)
        updateNotification(getString(R.string.status_disconnected))
        reconnectRunnable = Runnable {
            reconnectRunnable = null
            connect()
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    private fun refreshTrustState() {
        try {
            val http = OkHttpClient()
            val requestBuilder = Request.Builder()
                .url(
                    SyncEndpointUrls.httpUrl(
                        serverBase = config.serverBase,
                        path = "api/sync/bootstrap",
                        query = mapOf(
                            "room" to config.room,
                            "deviceId" to config.deviceId,
                        ),
                    ),
                )
            if (config.roomPassword.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.roomPassword}")
            }
            http.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val trusted = json.optJSONObject("device")?.optBoolean("trusted", false) == true
                client?.refreshTrusted(trusted)
                if (trusted) {
                    applyLatestRecentMessage(json, "已从最近同步恢复文本到剪贴板")
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun applyLatestRecentMessage(json: JSONObject, resultText: String) {
        val messages = json.optJSONArray("recentMessages") ?: return
        for (index in messages.length() - 1 downTo 0) {
            val item = messages.optJSONObject(index) ?: continue
            if (item.optString("sourceDeviceId").trim() == config.deviceId) continue
            val text = item.optString("text").trim()
            if (text.isBlank()) continue
            applyRemoteText(item.optString("messageId"), text, resultText)
            return
        }
    }

    private fun applyRemoteText(messageId: String, text: String, resultText: String) {
        if (text.isBlank()) return
        if (messageId.isNotBlank() && messageId == lastRemoteMessageId) return
        applyingRemoteText = true
        if (messageId.isNotBlank()) {
            lastRemoteMessageId = messageId
        }
        lastRemoteText = text
        lastRemoteAt = System.currentTimeMillis()
        suppressedRemoteEchoText = text
        lastObservedLocalText = text
        updateClipboardDiagnostic("remote-apply", resultText)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("cloud-clipboard", text))
        broadcastStatus(getString(R.string.status_trusted), resultText)
        handler.postDelayed({ applyingRemoteText = false }, 1500)
    }

    private fun publishLocalClipboardIfNeeded(source: String): Boolean {
        refreshConfig()
        if (applyingRemoteText) {
            updateClipboardDiagnostic("skip-$source", "刚完成远端文本写回，已跳过本次本地回传，避免自激同步")
            return false
        }
        if (!trusted) {
            updateClipboardDiagnostic("skip-$source", "设备尚未获批准，已跳过本次本地剪贴板处理")
            return false
        }
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull()
        if (clip == null) {
            updateClipboardDiagnostic(source, "系统当前没有可读取的剪贴板内容")
            if (shouldUseAccessibilitySnapshotFallback(source)) {
                return publishAccessibilitySnapshotFallback(source, "clipboard-null")
            }
            return false
        }
        if (clip.itemCount <= 0) {
            updateClipboardDiagnostic(source, "系统剪贴板为空，暂时没有可发送文本")
            if (shouldUseAccessibilitySnapshotFallback(source)) {
                return publishAccessibilitySnapshotFallback(source, "clipboard-empty")
            }
            return false
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        if (text.isBlank()) {
            updateClipboardDiagnostic(source, "本次剪贴板不是可发送的纯文本，已跳过")
            if (shouldUseAccessibilitySnapshotFallback(source)) {
                return publishAccessibilitySnapshotFallback(source, "clipboard-blank")
            }
            return false
        }
        val now = System.currentTimeMillis()
        if (shouldSuppressRemoteEcho(text)) {
            updateClipboardDiagnostic(source, "当前剪贴板仍是远端写入内容，已阻止回环发送")
            return false
        }
        if (text == lastObservedLocalText) {
            updateClipboardDiagnostic(source, "检测到的文本与上次一致，已忽略重复内容")
            return false
        }
        if (isRecentlyPublishedText(text, now)) {
            updateClipboardDiagnostic(source, "短时间内已发送过相同文本，已跳过")
            return false
        }
        if (config.clipboardMode == SettingsStore.CLIPBOARD_MODE_FLOATING) {
            lastObservedLocalText = text
            updateClipboardDiagnostic(source, "已检测到新的本地文本，准备弹出悬浮发送助手")
            broadcastStatus(currentStatus(), "已检测到新的本地文本，悬浮发送助手已准备好")
            if (PermissionStatusHelper.read(this).overlayEnabled) {
                FloatingClipboardOverlayService.show(this)
                updateClipboardDiagnostic(source, "已检测到新的本地文本，并弹出悬浮发送助手")
                broadcastStatus(currentStatus(), "已检测到新的本地文本，并弹出悬浮发送助手")
            }
            return false
        }
        lastObservedLocalText = text
        if (text == lastPublishedText && now - lastPublishedAt < 2_000) {
            updateClipboardDiagnostic(source, "短时间内检测到重复发布，已跳过")
            return false
        }
        return publishTextToServer(text, now, "已推送本地文本到服务端", source)
    }

    private fun shouldUseAccessibilitySnapshotFallback(source: String): Boolean {
        refreshConfig()
        if (config.clipboardMode != SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY) return false
        return source == "accessibility" || source == "listener"
    }

    private fun handleAccessibilityPulse(intent: Intent) {
        refreshConfig()
        val sourcePackage = intent.getStringExtra(EXTRA_ACCESSIBILITY_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_ACCESSIBILITY_REASON).orEmpty()
        if (!trusted) {
            broadcastStatus(currentStatus(), "无障碍补检查已触发，但设备还未获批准")
            return
        }
        val snapshot = ClipboardAccessAccessibilityService.consumeRecentSnapshot(sourcePackage)
        val snapshotText = snapshot?.text?.trim().orEmpty()
        val now = System.currentTimeMillis()
        if (snapshotText.isNotBlank() && shouldSuppressRemoteEcho(snapshotText)) {
            updateClipboardDiagnostic("accessibility-snapshot", "无障碍快照命中远端写入内容，已阻止回环发送")
            broadcastStatus(currentStatus(), "无障碍补检查已忽略回环文本")
            return
        }
        if (snapshot?.packageName == packageName) {
            updateClipboardDiagnostic("accessibility-snapshot", "无障碍快照来自本应用界面，已跳过")
            broadcastStatus(currentStatus(), "无障碍补检查已跳过本应用界面文本")
            return
        }
        if (snapshotText.isNotBlank() && isRecentlyPublishedText(snapshotText, now)) {
            updateClipboardDiagnostic("accessibility-snapshot", "无障碍快照短时间内已发送过相同文本，已跳过")
            broadcastStatus(currentStatus(), "无障碍补检查已跳过重复快照")
            return
        }
        if (snapshotText.isNotBlank() && snapshotText == lastPublishedText && now - lastPublishedAt < DUPLICATE_PUBLISH_SUPPRESS_MS) {
            updateClipboardDiagnostic("accessibility-snapshot", "无障碍快照短时间内检测到重复内容，已跳过")
            broadcastStatus(currentStatus(), "无障碍补检查已跳过重复内容")
            return
        }
        if (snapshot != null && snapshotText.isNotBlank() && publishTextToServer(snapshotText, now, buildString {
                append("已通过无障碍快照补传文本")
                if (snapshot.packageName.isNotBlank()) {
                    append(" · 来源 ")
                    append(snapshot.packageName)
                }
                if (reason.isNotBlank()) {
                    append(" · ")
                    append(reason.take(60))
                }
            }, "accessibility-snapshot")) {
            return
        }
        val detail = buildString {
            append("无障碍补检查已触发，但没有读取到新的剪贴板")
            if (sourcePackage.isNotBlank()) {
                append(" · 来源 ")
                append(sourcePackage)
            }
            if (reason.isNotBlank()) {
                append(" · ")
                append(reason.take(60))
            }
        }
        broadcastStatus(currentStatus(), detail)
    }

    private fun queueDebugPublish(intent: Intent) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return
        }
        val text = intent.getStringExtra(EXTRA_DEBUG_TEXT)?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }
        pendingDebugPublishText = text
    }

    private fun queueManualPublish(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_MANUAL_TEXT)?.trim().orEmpty()
        if (text.isBlank()) {
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                android.util.Log.d("SyncService", "queueManualPublish ignored blank text")
            }
            return
        }
        val route = intent.getStringExtra(EXTRA_MANUAL_ROUTE)?.trim().orEmpty().ifBlank { "manual" }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.d("SyncService", "queueManualPublish route=$route textLength=${text.length}")
        }
        enqueueManualPublish(text, route)
    }

    private fun enqueueManualPublish(text: String, route: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return false
        }
        pendingManualPublishText = normalized
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.d(
                "SyncService",
                "enqueueManualPublish route=$route textLength=${normalized.length} serviceStarted=$serviceStarted trusted=$trusted clientNull=${client == null}",
            )
        }
        updateClipboardDiagnostic(route, "已接收手动发送请求，等待同步连接可用")
        if (serviceStarted && trusted && client?.isConnected() == true) {
            flushPendingManualPublishText()
            return true
        }
        if (serviceStarted) {
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                android.util.Log.d(
                    "SyncService",
                    "enqueueManualPublish reconnectNow route=$route connected=${client?.isConnected() == true} clientNull=${client == null}",
                )
            }
            reconnectNow("manual-send")
        }
        return false
    }
    private fun flushPendingDebugPublishText() {
        val text = pendingDebugPublishText.trim()
        if (text.isBlank() || !trusted) {
            return
        }
        pendingDebugPublishText = ""
        val now = System.currentTimeMillis()
        lastObservedLocalText = text
        publishTextToServer(
            text,
            now,
            "已通过调试注入直接推送文本到服务端",
            "debug-inject",
        )
    }

    private fun flushPendingManualPublishText() {
        val text = pendingManualPublishText.trim()
        if (text.isBlank() || !trusted) {
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                android.util.Log.d("SyncService", "flushPendingManualPublishText skipped textBlank=${text.isBlank()} trusted=$trusted connected=${client?.isConnected() == true}")
            }
            return
        }
        if (client?.isConnected() != true) {
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                android.util.Log.d("SyncService", "flushPendingManualPublishText delayed because websocket is not connected")
            }
            reconnectNow("manual-send")
            return
        }
        pendingManualPublishText = ""
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.d("SyncService", "flushPendingManualPublishText publishing textLength=${text.length}")
        }
        val now = System.currentTimeMillis()
        lastObservedLocalText = text
        publishTextToServer(
            text,
            now,
            "已通过手动发送入口推送文本到服务端",
            "manual-send",
        )
    }
    private fun publishAccessibilitySnapshotFallback(source: String, fallbackReason: String): Boolean {
        val snapshot = ClipboardAccessAccessibilityService.consumeRecentSnapshot(sourcePackage = "") ?: return false
        val text = snapshot.text.trim()
        if (text.isBlank()) return false
        if (shouldSuppressRemoteEcho(text)) return false
        val now = System.currentTimeMillis()
        if (text == lastObservedLocalText) return false
        if (snapshot.packageName == packageName) return false
        if (isRecentlyPublishedText(text, now)) return false
        lastObservedLocalText = text
        if (text == lastPublishedText && now - lastPublishedAt < DUPLICATE_PUBLISH_SUPPRESS_MS) return false
        val result = buildString {
            append("已通过无障碍快照补传文本")
            if (snapshot.packageName.isNotBlank()) {
                append(" · 来源 ")
                append(snapshot.packageName)
            }
            append(" · ")
            append(fallbackReason)
        }
        return publishTextToServer(text, now, result, "$source-fallback")
    }

    private fun publishTextToServer(text: String, publishedAt: Long, resultText: String, route: String): Boolean {
        lastPublishedText = text
        lastPublishedAt = publishedAt
        rememberPublishedText(text, publishedAt)
        updateClipboardDiagnostic(route, resultText)
        client?.publishText(text)
        broadcastStatus(getString(R.string.status_trusted), resultText)
        return true
    }

    private fun readCurrentClipboardText(): String {
        refreshConfig()
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
    }

    private fun isRecentlyPublishedText(text: String, now: Long): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        pruneRecentPublishedTexts(now)
        val publishedAt = recentPublishedTexts[normalized] ?: return false
        return now - publishedAt < DUPLICATE_PUBLISH_SUPPRESS_MS
    }

    private fun rememberPublishedText(text: String, publishedAt: Long) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        recentPublishedTexts[normalized] = publishedAt
        pruneRecentPublishedTexts(publishedAt)
    }

    private fun pruneRecentPublishedTexts(now: Long) {
        val iterator = recentPublishedTexts.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > DUPLICATE_PUBLISH_SUPPRESS_MS * 2) {
                iterator.remove()
            }
        }
    }

    private fun shouldSuppressRemoteEcho(text: String): Boolean {
        if (text.isBlank() || suppressedRemoteEchoText.isBlank()) return false
        if (text == suppressedRemoteEchoText) return true
        suppressedRemoteEchoText = ""
        return false
    }

    private fun updateClipboardDiagnostic(route: String, detail: String) {
        lastClipboardRoute = route
        lastClipboardDetail = detail
    }

    private fun refreshConfig() {
        config = SettingsStore.load(this)
    }

    private fun currentStatus(): String = when {
        trusted -> getString(R.string.status_trusted)
        else -> getString(R.string.status_pending)
    }

    private fun shouldUseFloatingConfirm(): Boolean {
        val config = SettingsStore.load(this)
        return config.floatingEnabled && PermissionStatusHelper.read(this).overlayEnabled
    }

    private fun showReconnectFailureAlert(message: String) {
        if (shouldUseFloatingConfirm()) {
            FloatingConfirmService.showSyncAlert(
                this,
                getString(R.string.reconnect_failure_alert_title),
                message,
            )
            return
        }
        showReconnectFailureNotification(message)
    }

    private fun confirmPayloadDownload(payloadId: String) {
        if (downloadingPayloads.contains(payloadId)) return
        downloadingPayloads += payloadId
        Thread {
            try {
                PayloadCacheStore.pruneExpired(this)
                val entry = PayloadCacheStore.get(this, payloadId) ?: error("未找到待接收内容")
                broadcastStatus(currentStatus(), "开始下载 ${entry.title}")
                val downloaded = PayloadDownloader.download(this, config, entry)
                broadcastPayloadUpdated(downloaded.payloadId)
                showPayloadReadyNotification(downloaded)
                broadcastStatus(currentStatus(), "已下载到缓存：${downloaded.title}")
            } catch (error: Exception) {
                broadcastStatus(currentStatus(), "下载失败：${error.message ?: "未知错误"}")
            } finally {
                downloadingPayloads -= payloadId
            }
        }.start()
    }

    private fun showPayloadNoticeNotification(entry: PayloadEntry) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            payloadNotificationId(entry.payloadId),
            NotificationCompat.Builder(this, RECEIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentTitle(getString(R.string.payload_notice_title, describeKind(entry.kind)))
                .setContentText(getString(R.string.payload_notice_text, entry.title))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.payload_notice_text, entry.title)))
                .setAutoCancel(true)
                .setContentIntent(payloadActivityIntent(entry.payloadId))
                .addAction(
                    0,
                    getString(R.string.payload_confirm_download),
                    PendingIntent.getService(
                        this,
                        payloadNotificationId(entry.payloadId),
                        Intent(this, SyncService::class.java)
                            .setAction(ACTION_CONFIRM_PAYLOAD)
                            .putExtra(EXTRA_PAYLOAD_ID, entry.payloadId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    private fun showPayloadReadyNotification(entry: PayloadEntry) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            payloadNotificationId(entry.payloadId),
            NotificationCompat.Builder(this, RECEIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentTitle(getString(R.string.payload_ready_title))
                .setContentText(getString(R.string.payload_ready_text, entry.title))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.payload_ready_text, entry.title)))
                .setAutoCancel(true)
                .setContentIntent(payloadActivityIntent(entry.payloadId))
                .build(),
        )
    }

    private fun showReconnectFailureNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            RECONNECT_ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, RECEIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentTitle(getString(R.string.reconnect_failure_alert_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        RECONNECT_ALERT_NOTIFICATION_ID,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    private fun payloadActivityIntent(payloadId: String): PendingIntent = PendingIntent.getActivity(
        this,
        payloadNotificationId(payloadId),
        Intent(this, ReceivedPayloadActivity::class.java).putExtra(EXTRA_PAYLOAD_ID, payloadId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(status: String): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun broadcastStatus(status: String, lastResult: String) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_LAST_RESULT, lastResult)
            putExtra(EXTRA_CLIPBOARD_ROUTE, lastClipboardRoute)
            putExtra(EXTRA_CLIPBOARD_DETAIL, lastClipboardDetail)
        })
    }

    private fun broadcastPayloadUpdated(payloadId: String) {
        sendBroadcast(Intent(ACTION_PAYLOAD_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_PAYLOAD_ID, payloadId)
        })
    }

    private fun createChannel() {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_sync_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RECEIVE_CHANNEL_ID,
                getString(R.string.notification_channel_receive),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_receive_desc)
            },
        )
    }

    private fun payloadNotificationId(payloadId: String): Int {
        val normalized = payloadId.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }
        return 2000 + (normalized % 100000)
    }

    private fun describeKind(kind: String): String = when (kind) {
        "image" -> getString(R.string.payload_kind_image)
        "file" -> getString(R.string.payload_kind_file)
        else -> getString(R.string.payload_kind_unknown)
    }

    companion object {
        @Volatile
        private var activeInstance: SyncService? = null

        const val ACTION_STATUS = "com.transparentlc.cloudclipboardsync.STATUS"
        const val ACTION_PAYLOAD_UPDATED = "com.transparentlc.cloudclipboardsync.PAYLOAD_UPDATED"
        const val ACTION_DEBUG_PUBLISH_TEXT = "com.transparentlc.cloudclipboardsync.action.DEBUG_PUBLISH_TEXT"
        const val ACTION_SEND_MANUAL_TEXT = "com.transparentlc.cloudclipboardsync.action.SEND_MANUAL_TEXT"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_LAST_RESULT = "extra_last_result"
        const val EXTRA_CLIPBOARD_ROUTE = "extra_clipboard_route"
        const val EXTRA_CLIPBOARD_DETAIL = "extra_clipboard_detail"
        const val EXTRA_PAYLOAD_ID = "extra_payload_id"
        const val EXTRA_DEBUG_TEXT = "extra_debug_text"
        const val EXTRA_MANUAL_TEXT = "extra_manual_text"
        const val EXTRA_MANUAL_ROUTE = "extra_manual_route"
        private const val EXTRA_ACCESSIBILITY_PACKAGE = "extra_accessibility_package"
        private const val EXTRA_ACCESSIBILITY_REASON = "extra_accessibility_reason"

        private const val ACTION_CONFIRM_PAYLOAD = "com.transparentlc.cloudclipboardsync.action.CONFIRM_PAYLOAD"
        private const val ACTION_ACCESSIBILITY_PULSE = "com.transparentlc.cloudclipboardsync.action.ACCESSIBILITY_PULSE"

        const val CHANNEL_ID = "cloud_clipboard_sync"
        const val RECEIVE_CHANNEL_ID = "cloud_clipboard_receive"
        private const val NOTIFICATION_ID = 1001
        private const val RECONNECT_ALERT_NOTIFICATION_ID = 1002
        private const val DUPLICATE_PUBLISH_SUPPRESS_MS = 30_000L
        @Volatile
        private var isRunning = false

        fun isRunning(): Boolean = isRunning

        fun start(context: Context) {
            SettingsStore.setDesiredRunningState(context, true)
            ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))
        }

        fun confirmPayload(context: Context, payloadId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SyncService::class.java)
                    .setAction(ACTION_CONFIRM_PAYLOAD)
                    .putExtra(EXTRA_PAYLOAD_ID, payloadId),
            )
        }

        fun requestAccessibilityPulse(context: Context, sourcePackage: String, reason: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SyncService::class.java)
                    .setAction(ACTION_ACCESSIBILITY_PULSE)
                    .putExtra(EXTRA_ACCESSIBILITY_PACKAGE, sourcePackage)
                    .putExtra(EXTRA_ACCESSIBILITY_REASON, reason),
            )
        }

        fun sendManualText(context: Context, text: String, route: String) {
            activeInstance?.let { service ->
                if (service.enqueueManualPublish(text, route)) {
                    return
                }
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, SyncService::class.java)
                    .setAction(ACTION_SEND_MANUAL_TEXT)
                    .putExtra(EXTRA_MANUAL_TEXT, text)
                    .putExtra(EXTRA_MANUAL_ROUTE, route),
            )
        }

        fun stop(context: Context) {
            SettingsStore.setDesiredRunningState(context, false)
            context.stopService(Intent(context, SyncService::class.java))
        }
    }
}
