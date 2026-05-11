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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.transparentlc.cloudclipboardsync.FloatingConfirmService
import com.transparentlc.cloudclipboardsync.ReceivedPayloadActivity
import com.transparentlc.cloudclipboardsync.MainActivity
import com.transparentlc.cloudclipboardsync.PermissionStatusHelper
import com.transparentlc.cloudclipboardsync.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.abs

class SyncService : Service() {
    private val reconnectDelaysMs = longArrayOf(2_000L, 2_000L, 2_000L)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var config: SettingsStore.Config
    private var client: ClipboardSyncClient? = null
    private var applyingRemoteText = false
    private var trusted = false
    private var lastRemoteText = ""
    private var lastRemoteAt = 0L
    private var lastRemoteMessageId = ""
    private var lastPublishedText = ""
    private var lastPublishedAt = 0L
    private var serviceStarted = false
    private var reconnectAttempt = 0
    private val downloadingPayloads = mutableSetOf<String>()

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (applyingRemoteText || !trusted) return@OnPrimaryClipChangedListener
        val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) return@OnPrimaryClipChangedListener
        val now = System.currentTimeMillis()
        if (text == lastRemoteText && now - lastRemoteAt < 5_000) return@OnPrimaryClipChangedListener
        if (text == lastPublishedText && now - lastPublishedAt < 2_000) return@OnPrimaryClipChangedListener
        lastPublishedText = text
        lastPublishedAt = now
        client?.publishText(text)
        broadcastStatus(getString(R.string.status_trusted), "已推送本地文本到服务端")
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            Thread(::refreshTrustState).start()
            handler.postDelayed(this, 8_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        PayloadCacheStore.pruneExpired(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        config = SettingsStore.load(this)
        PayloadCacheStore.pruneExpired(this)
        when (intent?.action) {
            ACTION_CONFIRM_PAYLOAD -> intent.getStringExtra(EXTRA_PAYLOAD_ID)?.let(::confirmPayloadDownload)
        }
        if (!serviceStarted) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
            connect()
            handler.post(refreshRunnable)
            serviceStarted = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        client?.disconnect()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        client?.disconnect()
        client = ClipboardSyncClient(config, object : ClipboardSyncClient.Callbacks {
            override fun onConnected() {
                reconnectAttempt = 0
                broadcastStatus(getString(R.string.status_connected), "同步连接已建立")
                updateNotification(getString(R.string.status_connected))
            }

            override fun onTrustedChanged(trusted: Boolean) {
                this@SyncService.trusted = trusted
                val status = if (trusted) getString(R.string.status_trusted) else getString(R.string.status_pending)
                broadcastStatus(status, if (trusted) "设备已获批准" else "设备等待网页批准")
                updateNotification(status)
            }

            override fun onRemoteText(messageId: String, text: String) {
                applyRemoteText(messageId, text, "已接收远端文本并写入剪贴板")
            }

            override fun onPayloadNotice(notice: PayloadNotice) {
                val existing = PayloadCacheStore.get(this@SyncService, notice.payloadId)
                val entry = PayloadCacheStore.upsertNotice(this@SyncService, notice)
                if (existing?.isDownloaded != true) {
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
                broadcastStatus(getString(R.string.status_forbidden), "认证失败")
                updateNotification(getString(R.string.status_forbidden))
            }

            override fun onDisconnected() {
                scheduleReconnectOrStop()
            }
        })
        client?.connect()
    }

    private fun scheduleReconnectOrStop() {
        if (reconnectAttempt >= reconnectDelaysMs.size) {
            reconnectAttempt = 0
            val message = getString(R.string.reconnect_failure_limit_message)
            broadcastStatus(getString(R.string.status_disconnected), message)
            updateNotification(getString(R.string.status_disconnected))
            showReconnectFailureAlert(message)
            return
        }
        val attempt = reconnectAttempt + 1
        val delayMs = reconnectDelaysMs[reconnectAttempt]
        reconnectAttempt++
        val message = getString(R.string.reconnect_retry_message, attempt, reconnectDelaysMs.size)
        broadcastStatus(getString(R.string.status_disconnected), message)
        updateNotification(getString(R.string.status_disconnected))
        handler.postDelayed({ connect() }, delayMs)
    }

    private fun refreshTrustState() {
        try {
            val http = OkHttpClient()
            val requestBuilder = Request.Builder()
                .url("${config.serverBase.trimEnd('/')}/api/sync/bootstrap?room=${config.room}&deviceId=${config.deviceId}")
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
        clipboardManager.setPrimaryClip(ClipData.newPlainText("cloud-clipboard", text))
        broadcastStatus(getString(R.string.status_trusted), resultText)
        handler.postDelayed({ applyingRemoteText = false }, 1500)
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
                "Cloud Clipboard Sync",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RECEIVE_CHANNEL_ID,
                "Cloud Clipboard Receive",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
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
        const val ACTION_STATUS = "com.transparentlc.cloudclipboardsync.STATUS"
        const val ACTION_PAYLOAD_UPDATED = "com.transparentlc.cloudclipboardsync.PAYLOAD_UPDATED"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_LAST_RESULT = "extra_last_result"
        const val EXTRA_PAYLOAD_ID = "extra_payload_id"

        private const val ACTION_CONFIRM_PAYLOAD = "com.transparentlc.cloudclipboardsync.action.CONFIRM_PAYLOAD"

        private const val CHANNEL_ID = "cloud_clipboard_sync"
        private const val RECEIVE_CHANNEL_ID = "cloud_clipboard_receive"
        private const val NOTIFICATION_ID = 1001
        private const val RECONNECT_ALERT_NOTIFICATION_ID = 1002
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

        fun stop(context: Context) {
            SettingsStore.setDesiredRunningState(context, false)
            context.stopService(Intent(context, SyncService::class.java))
        }
    }
}
