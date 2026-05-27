package com.transparentlc.cloudclipboardsync

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService
import java.util.Locale

class ClipboardAccessAccessibilityService : AccessibilityService() {
    private var lastPulseAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!SyncService.isRunning()) return
        val config = SettingsStore.load(this)
        if (config.clipboardMode != SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName == this.packageName) return
        val now = System.currentTimeMillis()
        val pulseReason = detectPulseReason(event) ?: return
        if (now - lastPulseAt < 450L) return
        lastPulseAt = now
        SyncService.requestAccessibilityPulse(this, packageName, pulseReason)
    }

    override fun onInterrupt() = Unit

    private fun detectPulseReason(event: AccessibilityEvent): String? {
        val summary = buildString {
            event.text?.forEach { item ->
                val value = item?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append(value)
                }
            }
            val description = event.contentDescription?.toString()?.trim().orEmpty()
            if (description.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append(description)
            }
        }.trim()
        val normalized = summary.lowercase(Locale.ROOT)
        return when {
            normalized.contains("复制") -> "copy-ui:$summary"
            normalized.contains("copied") -> "copy-ui:$summary"
            normalized.contains("copy") -> "copy-ui:$summary"
            event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && summary.isNotBlank() -> "notify:$summary"
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED -> "click:${event.className ?: "-"}"
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window:${event.className ?: "-"}"
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "content:${event.className ?: "-"}"
            else -> null
        }
    }
}
