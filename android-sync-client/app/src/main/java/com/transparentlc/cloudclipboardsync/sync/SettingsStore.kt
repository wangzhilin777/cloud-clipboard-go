package com.transparentlc.cloudclipboardsync.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.UUID

object SettingsStore {
    private const val PREFS_NAME = "cloud_clipboard_sync"
    private const val KEY_SERVER_BASE = "server_base"
    private const val KEY_ROOM = "room"
    private const val KEY_ROOM_PASSWORD = "room_password"
    private const val KEY_AUTH_CODE_LEGACY = "auth_code"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    private const val KEY_START_ON_BOOT_ENABLED = "start_on_boot_enabled"
    private const val KEY_CLOSE_ACTIVITY_AFTER_START = "close_activity_after_start"
    private const val KEY_REMOVE_TASK_FROM_RECENTS = "remove_task_from_recents"
    private const val KEY_FLOATING_ENABLED = "floating_enabled"
    private const val KEY_FLOATING_WIDTH_DP = "floating_width_dp"
    private const val KEY_FLOATING_HEIGHT_DP = "floating_height_dp"
    private const val KEY_FLOATING_POS_X = "floating_pos_x"
    private const val KEY_FLOATING_POS_Y = "floating_pos_y"
    private const val KEY_FLOATING_SHOW_SECONDS = "floating_show_seconds"
    private const val KEY_FLOATING_SNOOZE_MINUTES = "floating_snooze_minutes"
    private const val KEY_FLOATING_COMPACT_ENABLED = "floating_compact_enabled"
    private const val KEY_CACHE_RETENTION_HOURS = "cache_retention_hours"
    private const val KEY_LAST_DESIRED_RUNNING_STATE = "last_desired_running_state"
    private const val KEY_CLIPBOARD_MODE = "clipboard_mode"

    const val CLIPBOARD_MODE_FOREGROUND = "foreground"
    const val CLIPBOARD_MODE_ACCESSIBILITY = "accessibility"
    const val CLIPBOARD_MODE_SHIZUKU = "shizuku"
    const val RUNNING_STATE_STOPPED = "stopped"
    const val RUNNING_STATE_RUNNING = "running"
    private const val LEGACY_DEFAULT_DEVICE_NAME = "Android 同步端"
    private const val DEFAULT_FLOATING_WIDTH_DP = 280
    private const val DEFAULT_FLOATING_HEIGHT_DP = 110
    private const val DEFAULT_FLOATING_POS_X = 48
    private const val DEFAULT_FLOATING_POS_Y = 220
    private const val DEFAULT_FLOATING_SHOW_SECONDS = 20
    private const val DEFAULT_FLOATING_SNOOZE_MINUTES = 10

    data class Config(
        val serverBase: String,
        val room: String,
        val roomPassword: String,
        val deviceName: String,
        val deviceId: String,
        val autoConnectEnabled: Boolean,
        val startOnBootEnabled: Boolean,
        val closeActivityAfterStart: Boolean,
        val removeTaskFromRecents: Boolean,
        val floatingEnabled: Boolean,
        val floatingWidthDp: Int,
        val floatingHeightDp: Int,
        val floatingPosX: Int,
        val floatingPosY: Int,
        val floatingShowSeconds: Int,
        val floatingSnoozeMinutes: Int,
        val floatingCompactEnabled: Boolean,
        val cacheRetentionHours: Int,
        val clipboardMode: String,
        val lastDesiredRunningState: String,
    )

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
        val resolvedDeviceName = resolveStoredDeviceName(context, prefs)
        return Config(
            serverBase = prefs.getString(KEY_SERVER_BASE, "") ?: "",
            room = prefs.getString(KEY_ROOM, "") ?: "",
            roomPassword = prefs.getString(KEY_ROOM_PASSWORD, prefs.getString(KEY_AUTH_CODE_LEGACY, "")) ?: "",
            deviceName = resolvedDeviceName,
            deviceId = deviceId,
            autoConnectEnabled = prefs.getBoolean(KEY_AUTO_CONNECT_ENABLED, true),
            startOnBootEnabled = prefs.getBoolean(KEY_START_ON_BOOT_ENABLED, false),
            closeActivityAfterStart = prefs.getBoolean(KEY_CLOSE_ACTIVITY_AFTER_START, false),
            removeTaskFromRecents = prefs.getBoolean(KEY_REMOVE_TASK_FROM_RECENTS, false),
            floatingEnabled = prefs.getBoolean(KEY_FLOATING_ENABLED, true),
            floatingWidthDp = prefs.getInt(KEY_FLOATING_WIDTH_DP, DEFAULT_FLOATING_WIDTH_DP),
            floatingHeightDp = prefs.getInt(KEY_FLOATING_HEIGHT_DP, DEFAULT_FLOATING_HEIGHT_DP),
            floatingPosX = prefs.getInt(KEY_FLOATING_POS_X, DEFAULT_FLOATING_POS_X),
            floatingPosY = prefs.getInt(KEY_FLOATING_POS_Y, DEFAULT_FLOATING_POS_Y),
            floatingShowSeconds = prefs.getInt(KEY_FLOATING_SHOW_SECONDS, DEFAULT_FLOATING_SHOW_SECONDS),
            floatingSnoozeMinutes = prefs.getInt(KEY_FLOATING_SNOOZE_MINUTES, DEFAULT_FLOATING_SNOOZE_MINUTES),
            floatingCompactEnabled = prefs.getBoolean(KEY_FLOATING_COMPACT_ENABLED, true),
            cacheRetentionHours = prefs.getInt(KEY_CACHE_RETENTION_HOURS, 24),
            clipboardMode = prefs.getString(KEY_CLIPBOARD_MODE, CLIPBOARD_MODE_FOREGROUND)
                ?.takeIf { it == CLIPBOARD_MODE_FOREGROUND || it == CLIPBOARD_MODE_ACCESSIBILITY || it == CLIPBOARD_MODE_SHIZUKU }
                ?: CLIPBOARD_MODE_FOREGROUND,
            lastDesiredRunningState = prefs.getString(KEY_LAST_DESIRED_RUNNING_STATE, RUNNING_STATE_STOPPED)
                ?.takeIf { it == RUNNING_STATE_RUNNING || it == RUNNING_STATE_STOPPED }
                ?: RUNNING_STATE_STOPPED,
        )
    }

    fun save(context: Context, config: Config) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_BASE, config.serverBase)
            .putString(KEY_ROOM, config.room)
            .putString(KEY_ROOM_PASSWORD, config.roomPassword)
            .putString(KEY_DEVICE_NAME, config.deviceName)
            .putString(KEY_DEVICE_ID, config.deviceId)
            .putBoolean(KEY_AUTO_CONNECT_ENABLED, config.autoConnectEnabled)
            .putBoolean(KEY_START_ON_BOOT_ENABLED, config.startOnBootEnabled)
            .putBoolean(KEY_CLOSE_ACTIVITY_AFTER_START, config.closeActivityAfterStart)
            .putBoolean(KEY_REMOVE_TASK_FROM_RECENTS, config.removeTaskFromRecents)
            .putBoolean(KEY_FLOATING_ENABLED, config.floatingEnabled)
            .putInt(KEY_FLOATING_WIDTH_DP, config.floatingWidthDp)
            .putInt(KEY_FLOATING_HEIGHT_DP, config.floatingHeightDp)
            .putInt(KEY_FLOATING_POS_X, config.floatingPosX)
            .putInt(KEY_FLOATING_POS_Y, config.floatingPosY)
            .putInt(KEY_FLOATING_SHOW_SECONDS, config.floatingShowSeconds)
            .putInt(KEY_FLOATING_SNOOZE_MINUTES, config.floatingSnoozeMinutes)
            .putBoolean(KEY_FLOATING_COMPACT_ENABLED, config.floatingCompactEnabled)
            .putInt(KEY_CACHE_RETENTION_HOURS, config.cacheRetentionHours)
            .putString(KEY_CLIPBOARD_MODE, config.clipboardMode)
            .putString(KEY_LAST_DESIRED_RUNNING_STATE, config.lastDesiredRunningState)
            .remove(KEY_AUTH_CODE_LEGACY)
            .apply()
    }

    fun resolveDeviceNameForSave(context: Context, inputValue: String): String {
        val trimmedInput = inputValue.trim()
        if (trimmedInput.isNotBlank()) {
            return trimmedInput
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedValue = prefs.getString(KEY_DEVICE_NAME, null).normalizeStoredDeviceName()
        return storedValue ?: detectLocalDeviceName(context)
    }

    fun setDesiredRunningState(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_DESIRED_RUNNING_STATE, if (running) RUNNING_STATE_RUNNING else RUNNING_STATE_STOPPED)
            .apply()
    }

    fun shouldResumeSync(context: Context): Boolean {
        val config = load(context)
        return config.autoConnectEnabled && config.lastDesiredRunningState == RUNNING_STATE_RUNNING
    }

    fun isLoopbackServerBase(serverBase: String): Boolean {
        if (serverBase.isBlank()) return false
        val host = runCatching { Uri.parse(serverBase).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    fun updateFloatingPosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FLOATING_POS_X, x)
            .putInt(KEY_FLOATING_POS_Y, y)
            .apply()
    }

    fun resetFloatingPosition(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FLOATING_POS_X, DEFAULT_FLOATING_POS_X)
            .putInt(KEY_FLOATING_POS_Y, DEFAULT_FLOATING_POS_Y)
            .apply()
    }

    fun detectLocalDeviceName(context: Context): String {
        val systemName = Settings.Global.getString(context.contentResolver, "device_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (systemName != null) {
            return systemName
        }

        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        if (manufacturer.isBlank() && model.isBlank()) {
            return LEGACY_DEFAULT_DEVICE_NAME
        }
        if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank()) {
            return model.ifBlank { LEGACY_DEFAULT_DEVICE_NAME }
        }
        return "$manufacturer $model".trim()
    }

    private fun resolveStoredDeviceName(context: Context, prefs: android.content.SharedPreferences): String {
        val storedValue = prefs.getString(KEY_DEVICE_NAME, null).normalizeStoredDeviceName()
        if (storedValue != null) {
            return storedValue
        }

        val detectedName = detectLocalDeviceName(context)
        prefs.edit().putString(KEY_DEVICE_NAME, detectedName).apply()
        return detectedName
    }

    private fun String?.normalizeStoredDeviceName(): String? {
        val trimmed = this?.trim()
        if (trimmed.isNullOrBlank()) {
            return null
        }
        if (trimmed == LEGACY_DEFAULT_DEVICE_NAME) {
            return null
        }
        return trimmed
    }
}
