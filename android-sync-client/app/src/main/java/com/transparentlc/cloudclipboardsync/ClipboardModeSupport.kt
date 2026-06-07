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
                    implementationSummary = "无障碍增强会在系统剪贴板回调之外，结合界面交互触发补检查，适合高版本 Android 的后台复制回传场景。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_accessibility_blocked),
                    implementationSummary = "无障碍增强需要先开启系统无障碍服务，开启后才能按该模式启动同步。",
                )
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_blocked),
                    implementationSummary = "当前设备还没有可用的 Shizuku 环境，请先安装 Shizuku 并启动服务。",
                )

                !status.shizukuRunning -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_not_running),
                    implementationSummary = "已检测到 Shizuku App，但服务还没有运行；如果使用 root 启动 Shizuku，请先在 Shizuku 内确认服务状态。",
                )

                !status.shizukuPermissionGranted -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_permission_required),
                    implementationSummary = "Shizuku 服务已运行，但云剪同步还没获得授权；点快捷处理按钮可以直接弹出授权请求。",
                )

                else -> ClipboardModeSupport(
                    canStart = true,
                    readyMessage = context.getString(R.string.runtime_mode_shizuku_ready),
                    implementationSummary = "Shizuku 服务已运行且云剪同步已授权；同步服务会正常启动，并把系统剪贴板 AppOps 状态纳入诊断。后台复制不稳时仍建议优先使用无障碍增强模式。",
                )
            }
        }

        else -> {
            ClipboardModeSupport(
                canStart = true,
                readyMessage = "前台服务模式已就绪。",
                implementationSummary = "前台服务模式开箱可用，适合前台使用、自动续连、文本同步和图片/文件确认接收。",
            )
        }
    }
}
