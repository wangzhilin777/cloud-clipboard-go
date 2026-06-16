package com.transparentlc.cloudclipboardsync

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.PayloadNotice
import com.transparentlc.cloudclipboardsync.sync.SyncService
import java.util.UUID

class DebugClipboardInjectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            return
        }
        when (intent.action) {
            ACTION_DEBUG_SET_CLIPBOARD_ONLY -> {
                val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                if (text.isBlank()) {
                    Log.w(TAG, "忽略空白调试剪贴板写入")
                    return
                }
                writeClipboard(context, text)
                Log.i(TAG, "已写入调试剪贴板但未触发同步，length=${text.length}")
            }

            ACTION_DEBUG_SHIZUKU_SMOKE -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent()
                        .setClassName(context, SYNC_SERVICE_CLASS_NAME)
                        .setAction(SyncService.ACTION_DEBUG_SHIZUKU_SMOKE),
                )
                Log.i(TAG, "已请求 Shizuku 辅助模式 smoke")
            }

            ACTION_DEBUG_INJECT_CLIPBOARD -> {
                val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                if (text.isBlank()) {
                    Log.w(TAG, "忽略空白调试剪贴板注入")
                    return
                }
                writeClipboard(context, text)
                ContextCompat.startForegroundService(
                    context,
                    Intent()
                        .setClassName(context, SYNC_SERVICE_CLASS_NAME)
                        .setAction(ACTION_DEBUG_PUBLISH_TEXT)
                        .putExtra(EXTRA_DEBUG_TEXT, text),
                )
            }

            ACTION_DEBUG_SEND_CLIPBOARD -> {
                val overrideText = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                val route = intent.getStringExtra(EXTRA_ROUTE)?.trim().orEmpty().ifBlank { "debug-manual" }
                if (overrideText.isNotBlank()) {
                    writeClipboard(context, overrideText)
                    ContextCompat.startForegroundService(
                        context,
                        Intent()
                            .setClassName(context, SYNC_SERVICE_CLASS_NAME)
                            .setAction(ACTION_SEND_MANUAL_TEXT)
                            .putExtra(EXTRA_MANUAL_TEXT, overrideText)
                            .putExtra(EXTRA_MANUAL_ROUTE, route),
                    )
                    Log.i(TAG, "已直接请求调试手动发送，route=$route length=${overrideText.length}")
                    return
                }
                val currentText = readClipboard(context)
                if (currentText.isBlank()) {
                    Log.w(TAG, "调试手动发送失败：当前剪贴板为空")
                    return
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent()
                        .setClassName(context, SYNC_SERVICE_CLASS_NAME)
                        .setAction(ACTION_SEND_MANUAL_TEXT)
                        .putExtra(EXTRA_MANUAL_TEXT, currentText)
                        .putExtra(EXTRA_MANUAL_ROUTE, route),
                )
                Log.i(TAG, "已读取当前剪贴板并请求调试手动发送，route=$route length=${currentText.length}")
            }

            ACTION_DEBUG_SHOW_FLOATING_CLIPBOARD -> {
                val overrideText = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                if (overrideText.isNotBlank()) {
                    writeClipboard(context, overrideText)
                }
                FloatingClipboardOverlayService.show(context)
                Log.i(TAG, "已请求弹出调试悬浮发送助手")
            }

            ACTION_DEBUG_SHOW_FLOATING_RECEIVE -> {
                val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "调试图片.png" }
                val kind = intent.getStringExtra(EXTRA_KIND)?.trim().orEmpty().ifBlank { "image" }
                val mime = when {
                    kind == "image" -> "image/png"
                    else -> "application/octet-stream"
                }
                val payloadId = "debug-${UUID.randomUUID()}"
                val notice = PayloadNotice(
                    payloadId = payloadId,
                    sourceDeviceId = "debug-device",
                    room = "default",
                    kind = kind,
                    title = title,
                    mime = mime,
                    size = 1024L,
                    actionUrl = null,
                    downloadUrl = "http://127.0.0.1:9/debug/$payloadId",
                    createdAt = System.currentTimeMillis(),
                )
                PayloadCacheStore.upsertNotice(context, notice)
                FloatingConfirmService.show(context, payloadId)
                Log.i(TAG, "已请求弹出调试悬浮接收卡片 payloadId=$payloadId title=$title")
            }
        }
    }

    private fun readClipboard(context: Context): String {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = runCatching { clipboardManager?.primaryClip }.getOrNull()
        if (clip == null || clip.itemCount <= 0) {
            return ""
        }
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
    }

    private fun writeClipboard(context: Context, text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("cloud-clipboard-debug", text))
        Log.i(TAG, "已写入调试剪贴板，length=${text.length}")
    }

    companion object {
        private const val TAG = "DebugClipboardInject"
        private const val SYNC_SERVICE_CLASS_NAME = "com.transparentlc.cloudclipboardsync.sync.SyncService"
        const val ACTION_DEBUG_INJECT_CLIPBOARD = "com.transparentlc.cloudclipboardsync.action.DEBUG_INJECT_CLIPBOARD"
        const val ACTION_DEBUG_SET_CLIPBOARD_ONLY = "com.transparentlc.cloudclipboardsync.action.DEBUG_SET_CLIPBOARD_ONLY"
        const val ACTION_DEBUG_SHIZUKU_SMOKE = "com.transparentlc.cloudclipboardsync.action.DEBUG_SHIZUKU_SMOKE"
        const val ACTION_DEBUG_SEND_CLIPBOARD = "com.transparentlc.cloudclipboardsync.action.DEBUG_SEND_CLIPBOARD"
        const val ACTION_DEBUG_SHOW_FLOATING_CLIPBOARD = "com.transparentlc.cloudclipboardsync.action.DEBUG_SHOW_FLOATING_CLIPBOARD"
        const val ACTION_DEBUG_SHOW_FLOATING_RECEIVE = "com.transparentlc.cloudclipboardsync.action.DEBUG_SHOW_FLOATING_RECEIVE"
        private const val ACTION_DEBUG_PUBLISH_TEXT = "com.transparentlc.cloudclipboardsync.action.DEBUG_PUBLISH_TEXT"
        private const val ACTION_SEND_MANUAL_TEXT = "com.transparentlc.cloudclipboardsync.action.SEND_MANUAL_TEXT"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ROUTE = "extra_route"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_KIND = "extra_kind"
        private const val EXTRA_DEBUG_TEXT = "extra_debug_text"
        private const val EXTRA_MANUAL_TEXT = "extra_manual_text"
        private const val EXTRA_MANUAL_ROUTE = "extra_manual_route"
    }
}
