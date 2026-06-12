package com.transparentlc.cloudclipboardsync

import android.content.ClipboardManager
import android.content.Context
import com.transparentlc.cloudclipboardsync.sync.SyncService

object ManualClipboardSender {
    data class ClipboardPreview(
        val text: String,
        val empty: Boolean,
    )

    fun readCurrentClipboardText(context: Context): String {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = runCatching { clipboardManager?.primaryClip }.getOrNull()
        if (clip == null || clip.itemCount <= 0) {
            return ""
        }
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
    }

    fun buildClipboardPreview(context: Context, maxChars: Int = 120): ClipboardPreview {
        val text = readCurrentClipboardText(context)
        if (text.isBlank()) {
            return ClipboardPreview(
                text = context.getString(R.string.clipboard_ime_preview_empty),
                empty = true,
            )
        }
        val normalized = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val preview = if (normalized.length <= maxChars) {
            normalized
        } else {
            normalized.take(maxChars).trimEnd() + "…"
        }
        return ClipboardPreview(text = preview, empty = false)
    }

    fun sendCurrentClipboardText(
        context: Context,
        route: String,
        onStatus: (String) -> Unit,
    ): Boolean {
        val text = readCurrentClipboardText(context)
        if (text.isBlank()) {
            onStatus(context.getString(R.string.clipboard_ime_empty_clipboard))
            return false
        }
        SyncService.sendManualText(context, text, route)
        onStatus(context.getString(R.string.clipboard_ime_sent))
        return true
    }

    fun sendText(
        context: Context,
        text: String,
        route: String,
        onStatus: (String) -> Unit,
    ): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            onStatus(context.getString(R.string.manual_text_empty))
            return false
        }
        SyncService.sendManualText(context, normalized, route)
        onStatus(context.getString(R.string.manual_text_sent))
        return true
    }
}
