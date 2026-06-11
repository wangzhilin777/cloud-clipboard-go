package com.transparentlc.cloudclipboardsync

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        cacheVisibleTextSnapshot(packageName, pulseReason, now)
        if (now - lastPulseAt < 450L) return
        lastPulseAt = now
        SyncService.requestAccessibilityPulse(this, packageName, pulseReason)
    }

    override fun onInterrupt() = Unit

    private fun detectPulseReason(event: AccessibilityEvent): String? {
        val summary = buildString {
            event.text.forEach { item ->
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
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED && summary.isNotBlank() -> "selection:${event.className ?: "-"}"
            else -> null
        }
    }

    private fun cacheVisibleTextSnapshot(packageName: String, pulseReason: String, now: Long) {
        if (now - lastSnapshotAt < 250L) return
        if (!pulseReason.startsWith("copy-ui:") && !pulseReason.startsWith("notify:") && !pulseReason.startsWith("selection:")) return
        val root = rootInActiveWindow ?: return
        val rootPackageName = root.packageName?.toString().orEmpty()
        if (rootPackageName == this.packageName) return
        val candidates = mutableListOf<TextCandidate>()
        collectVisibleTextCandidates(root, candidates, 0)
        val candidate = buildSnapshotText(candidates)
        if (candidate.isBlank()) return
        lastSnapshotAt = now
        updateSnapshot(rootPackageName.ifBlank { packageName }, pulseReason, candidate, now)
    }

    private fun collectVisibleTextCandidates(node: AccessibilityNodeInfo, candidates: MutableList<TextCandidate>, depth: Int) {
        if (depth > 8 || candidates.size >= 48) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) {
            val selected = selectedText(node, text)
            if (selected.isNotBlank()) {
                candidates += TextCandidate(selected, 0, depth)
            } else {
                val className = node.className?.toString().orEmpty()
                val priority = when {
                    node.isFocused && (node.isEditable || className.contains("EditText")) -> 1
                    node.isEditable || className.contains("EditText") -> 2
                    text.length >= 6 -> 3
                    else -> 5
                }
                val hint = node.hintText?.toString()?.trim().orEmpty()
                candidates += TextCandidate(
                    text = text,
                    priority = priority,
                    depth = depth,
                    editable = node.isEditable || className.contains("EditText"),
                    hint = hint,
                )
            }
        }
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (description.isNotBlank()) {
            candidates += TextCandidate(description, 4, depth)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleTextCandidates(child, candidates, depth + 1)
        }
    }

    private fun selectedText(node: AccessibilityNodeInfo, text: String): String {
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        if (start < 0 || end < 0 || start == end) return ""
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        if (from >= to) return ""
        return text.substring(from, to).trim()
    }

    private fun buildSnapshotText(candidates: List<TextCandidate>): String {
        return AccessibilitySnapshotSelector.buildSnapshotText(candidates)
    }

    companion object {
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

internal data class TextCandidate(
    val text: String,
    val priority: Int,
    val depth: Int,
    val editable: Boolean = false,
    val hint: String = "",
)

internal object AccessibilitySnapshotSelector {
    fun buildSnapshotText(candidates: List<TextCandidate>): String {
        val normalized = candidates
            .asSequence()
            .map {
                it.copy(
                    text = it.text.trim(),
                    hint = it.hint.trim(),
                )
            }
            .filter { it.text.isNotBlank() }
            .filterNot { it.text.length == 1 && it.text[0] == '×' }
            .filterNot(::isInputPlaceholderCandidate)
            .distinctBy { it.text }
            .sortedWith(compareBy<TextCandidate> { effectivePriority(it) }.thenBy { it.depth }.thenByDescending { it.text.length })
            .toList()
        if (normalized.isEmpty()) return ""
        val best = normalized.first()
        if (effectivePriority(best) <= 2) {
            return best.text.take(4000)
        }
        return normalized
            .asSequence()
            .filter { effectivePriority(it) <= 4 }
            .map { it.text }
            .take(8)
            .joinToString("\n")
            .trim()
            .take(4000)
    }

    private fun effectivePriority(candidate: TextCandidate): Int = when {
        looksLikeStructuredCopyText(candidate.text) -> 0
        else -> candidate.priority
    }

    private fun isInputPlaceholderCandidate(candidate: TextCandidate): Boolean {
        if (!candidate.editable) return false
        val normalizedText = normalize(candidate.text)
        if (normalizedText.isBlank()) return true
        val normalizedHint = normalize(candidate.hint)
        if (normalizedHint.isNotBlank() && normalizedText == normalizedHint) return true
        return normalizedText in PLACEHOLDER_TEXTS
    }

    private fun looksLikeStructuredCopyText(text: String): Boolean {
        val value = text.trim()
        if (value.isBlank()) return false
        if (value.contains("://")) return true
        if (value.contains('/') && value.contains('.')) return true
        if (value.contains('?') && value.contains('=')) return true
        return false
    }

    private fun normalize(text: String): String = text.trim().lowercase(Locale.ROOT)

    private val PLACEHOLDER_TEXTS = setOf(
        "在 google 中搜索或输入网址",
        "在google中搜索或输入网址",
        "搜索或输入网址",
        "search or type web address",
        "search or type url",
    )
}
