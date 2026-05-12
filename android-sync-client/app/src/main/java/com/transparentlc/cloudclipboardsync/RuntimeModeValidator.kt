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
        return when (config.clipboardMode) {
            SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
                if (status.accessibilityEnabled) {
                    RuntimeModeValidation(true, "无障碍增强模式已就绪。")
                } else {
                    RuntimeModeValidation(
                        ready = false,
                        message = context.getString(R.string.runtime_mode_accessibility_blocked),
                        action = RuntimeModeAction.OPEN_ACCESSIBILITY,
                    )
                }
            }

            SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
                if (status.shizukuInstalled) {
                    RuntimeModeValidation(true, "Shizuku 模式已可尝试启动。")
                } else {
                    RuntimeModeValidation(
                        ready = false,
                        message = context.getString(R.string.runtime_mode_shizuku_blocked),
                        action = RuntimeModeAction.OPEN_SHIZUKU,
                    )
                }
            }

            else -> RuntimeModeValidation(true, "前台服务模式已就绪。")
        }
    }
}
