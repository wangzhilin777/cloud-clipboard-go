package com.transparentlc.cloudclipboardsync

import android.content.Context
import com.transparentlc.cloudclipboardsync.sync.SettingsStore

data class RuntimeModeValidation(
    val ready: Boolean,
    val message: String,
    val action: RuntimeModeAction = RuntimeModeAction.NONE,
)

enum class RuntimeModeAction {
    NONE,
    OPEN_ACCESSIBILITY,
    OPEN_SHIZUKU,
}

object RuntimeModeValidator {
    fun validate(context: Context, config: SettingsStore.Config): RuntimeModeValidation {
        val status = PermissionStatusHelper.read(context)
        val support = ClipboardModeSupportHelper.describe(context, config.clipboardMode, status)
        if (support.canStart) {
            return RuntimeModeValidation(true, support.readyMessage)
        }
        return when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> RuntimeModeValidation(
                ready = false,
                message = support.blockedMessage ?: context.getString(R.string.runtime_mode_accessibility_blocked),
                action = RuntimeModeAction.OPEN_ACCESSIBILITY,
            )

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> RuntimeModeValidation(
                ready = false,
                message = support.blockedMessage ?: context.getString(R.string.runtime_mode_shizuku_blocked),
                action = RuntimeModeAction.OPEN_SHIZUKU,
            )

            else -> RuntimeModeValidation(true, support.readyMessage)
        }
    }
}
