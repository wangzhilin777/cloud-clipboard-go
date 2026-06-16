package com.transparentlc.cloudclipboardsync.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreModeMigrationTest {
    @Test
    fun normalizeClipboardModeMigratesLegacyModesToForeground() {
        assertEquals(SettingsStore.CLIPBOARD_MODE_FOREGROUND, SettingsStore.normalizeClipboardMode(SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY))
        assertEquals(SettingsStore.CLIPBOARD_MODE_FOREGROUND, SettingsStore.normalizeClipboardMode(SettingsStore.CLIPBOARD_MODE_IME))
    }

    @Test
    fun normalizeClipboardModeKeepsFormalModes() {
        assertEquals(SettingsStore.CLIPBOARD_MODE_FOREGROUND, SettingsStore.normalizeClipboardMode(SettingsStore.CLIPBOARD_MODE_FOREGROUND))
        assertEquals(SettingsStore.CLIPBOARD_MODE_FLOATING, SettingsStore.normalizeClipboardMode(SettingsStore.CLIPBOARD_MODE_FLOATING))
        assertEquals(SettingsStore.CLIPBOARD_MODE_SHIZUKU, SettingsStore.normalizeClipboardMode(SettingsStore.CLIPBOARD_MODE_SHIZUKU))
        assertEquals(SettingsStore.CLIPBOARD_MODE_FOREGROUND, SettingsStore.normalizeClipboardMode("unknown"))
        assertEquals(SettingsStore.CLIPBOARD_MODE_FOREGROUND, SettingsStore.normalizeClipboardMode(null))
    }
}
