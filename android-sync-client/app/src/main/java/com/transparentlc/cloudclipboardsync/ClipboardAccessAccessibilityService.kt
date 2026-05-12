package com.transparentlc.cloudclipboardsync

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

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
        if (now - lastPulseAt < 1200L) return
        lastPulseAt = now
        SyncService.requestAccessibilityPulse(this)
    }

    override fun onInterrupt() = Unit
}
