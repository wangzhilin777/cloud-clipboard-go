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
            when {
                !status.shizukuInstalled -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_blocked),
                    implementationSummary = "当前阶段：Shizuku 入口已接入；当前设备还没有安装 Shizuku，请先安装并启动服务。",
                )

                !status.shizukuRunning -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_not_running),
                    implementationSummary = "当前阶段：已检测到 Shizuku App，但服务还没有运行；如果使用 root 启动 Shizuku，请先在 Shizuku 内确认服务状态。",
                )

                !status.shizukuPermissionGranted -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_permission_required),
                    implementationSummary = "当前阶段：Shizuku 服务已运行，但云剪同步还没获得授权；点快捷处理按钮可以直接弹出授权请求。",
                )

                else -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_unavailable),
                    implementationSummary = "当前阶段：Shizuku 服务已运行且云剪同步已授权，但独立剪贴板增强链路还在接入中；本阶段请继续优先使用无障碍增强模式。",
                )
            }
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
