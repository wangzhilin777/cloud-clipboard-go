package com.transparentlc.cloudclipboardsync

import android.content.Context
import com.transparentlc.cloudclipboardsync.sync.SettingsStore

data class ClipboardModeSupport(
    val canStart: Boolean,
    val readyMessage: String,
    val blockedMessage: String? = null,
    val implementationSummary: String,
)

object ClipboardModeSupportHelper {
    fun describe(context: Context, mode: String, status: PermissionStatus): ClipboardModeSupport = when (mode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
            if (status.accessibilityEnabled) {
                ClipboardModeSupport(
                    canStart = true,
                    readyMessage = "无障碍增强模式已就绪。",
                    implementationSummary = "当前阶段：无障碍模式已接入授权检查、启动前置校验、恢复流程，以及界面交互触发的剪贴板补检查；后续会继续补强后台增强细节。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_accessibility_blocked),
                    implementationSummary = "当前阶段：无障碍模式已经接好配置入口和补检查链路，但还需要先开启系统无障碍服务，开启后才会按该模式启动同步。",
                )
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            ClipboardModeSupport(
                canStart = false,
                readyMessage = "",
                blockedMessage = context.getString(R.string.runtime_mode_shizuku_unavailable),
                implementationSummary = "当前阶段：Shizuku 仅完成配置入口和状态探测，还没有接入独立的增强实现；本阶段请优先使用前台服务或无障碍模式。",
            )
        }

        else -> {
            ClipboardModeSupport(
                canStart = true,
                readyMessage = "前台服务模式已就绪。",
                implementationSummary = "当前阶段：前台服务模式是一期开箱可用的主通道，文本同步、自动续连、图片/文件确认接收都按这条链路稳定运行。",
            )
        }
    }
}
