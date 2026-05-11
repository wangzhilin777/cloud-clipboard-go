package com.transparentlc.cloudclipboardsync.sync

import android.content.Context
import java.util.UUID

object SettingsStore {
    private const val PREFS_NAME = "cloud_clipboard_sync"
    private const val KEY_SERVER_BASE = "server_base"
    private const val KEY_ROOM = "room"
    private const val KEY_ROOM_PASSWORD = "room_password"
    private const val KEY_AUTH_CODE_LEGACY = "auth_code"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_ID = "device_id"

    data class Config(
        val serverBase: String,
        val room: String,
        val roomPassword: String,
        val deviceName: String,
        val deviceId: String,
    )

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
        return Config(
            serverBase = prefs.getString(KEY_SERVER_BASE, "http://127.0.0.1:9501") ?: "http://127.0.0.1:9501",
            room = prefs.getString(KEY_ROOM, "") ?: "",
            roomPassword = prefs.getString(KEY_ROOM_PASSWORD, prefs.getString(KEY_AUTH_CODE_LEGACY, "")) ?: "",
            deviceName = prefs.getString(KEY_DEVICE_NAME, "Android 同步端") ?: "Android 同步端",
            deviceId = deviceId,
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
            .remove(KEY_AUTH_CODE_LEGACY)
            .apply()
    }
}
