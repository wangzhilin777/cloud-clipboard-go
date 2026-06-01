package com.transparentlc.cloudclipboardsync

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService
import java.util.Locale

class ClipboardAccessAccessibilityService : AccessibilityService() {
    private var lastPulseAt = 0L
    private var lastSnapshotAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!SyncService.isRunning()) return
        val config = SettingsStore.load(this)
        if (config.clipboardMode != SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName == this.packageName) return
        val now = System.currentTimeMillis()
        val pulseReason = detectPulseReason(event) ?: return
        Log.d(TAG, "accessibility pulse package=$packageName reason=$pulseReason")
        cacheVisibleTextSnapshot(packageName, pulseReason, now)
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

    private fun cacheVisibleTextSnapshot(packageName: String, pulseReason: String, now: Long) {
        if (now - lastSnapshotAt < 250L) return
        val root = rootInActiveWindow ?: return
        val texts = linkedSetOf<String>()
        collectVisibleTexts(root, texts, 0)
        val candidate = texts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.length == 1 && it[0] == '×' }
            .joinToString("\n")
            .trim()
            .take(4000)
        if (candidate.isBlank()) return
        lastSnapshotAt = now
        Log.d(TAG, "snapshot cached package=$packageName reason=$pulseReason size=${candidate.length}")
        updateSnapshot(packageName, pulseReason, candidate, now)
    }

    private fun collectVisibleTexts(node: AccessibilityNodeInfo, texts: MutableSet<String>, depth: Int) {
        if (depth > 8 || texts.size >= 24) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) {
            texts += text
        }
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (description.isNotBlank()) {
            texts += description
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleTexts(child, texts, depth + 1)
        }
    }

    companion object {
        private const val TAG = "ClipboardAccessA11y"
        @Volatile private var lastSnapshotText: String = ""
        @Volatile private var lastSnapshotPackage: String = ""
        @Volatile private var lastSnapshotReason: String = ""
        @Volatile private var lastSnapshotAtMs: Long = 0L

        fun consumeRecentSnapshot(sourcePackage: String, maxAgeMs: Long = 4000L): SnapshotPayload? {
            val now = System.currentTimeMillis()
            val age = now - lastSnapshotAtMs
            if (age !in 0..maxAgeMs) return null
            if (lastSnapshotText.isBlank()) return null
            if (sourcePackage.isNotBlank() && lastSnapshotPackage.isNotBlank() && sourcePackage != lastSnapshotPackage) {
                return null
            }
            return SnapshotPayload(
                text = lastSnapshotText,
                packageName = lastSnapshotPackage,
                reason = lastSnapshotReason,
                capturedAt = lastSnapshotAtMs,
            )
        }

        private fun updateSnapshot(packageName: String, reason: String, text: String, capturedAt: Long) {
            lastSnapshotPackage = packageName
            lastSnapshotReason = reason
            lastSnapshotText = text
            lastSnapshotAtMs = capturedAt
        }
    }
}

data class SnapshotPayload(
    val text: String,
    val packageName: String,
    val reason: String,
    val capturedAt: Long,
)
